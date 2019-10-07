package discourse

import scala.language.higherKinds

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import ore.db.ModelService
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTableMain, VersionTable}
import ore.models.project.{Project, Version, Visibility}
import util.TaskUtils

import akka.actor.Scheduler
import cats.Parallel
import cats.effect.syntax.all._
import cats.syntax.all._
import com.typesafe.scalalogging

/**
  * Task to periodically retry failed Discourse requests.
  */
class RecoveryTask[F[_], G[_]](scheduler: Scheduler, retryRate: FiniteDuration, api: OreDiscourseApi[F])(
    implicit ec: ExecutionContext,
    service: ModelService[F],
    par: Parallel[F, G],
    effect: cats.effect.Effect[F]
) extends Runnable {

  val Logger: scalalogging.Logger = scalalogging.Logger("Discourse")

  private val projectTopicFilter = ModelFilter(Project)(_.topicId.isEmpty)
  private val projectDirtyFilter = ModelFilter(Project)(_.isTopicDirty)
  private val visibleFilter      = Visibility.isPublicFilter[ProjectTableMain]

  private val toCreateProjects = ModelView.raw(Project).filter(projectTopicFilter && visibleFilter).to[Vector]
  private val dirtyTopicProjects =
    ModelView.raw(Project).filter(!projectTopicFilter && projectDirtyFilter && visibleFilter).to[Vector]

  private val versionsQueryBase = for {
    (version, project) <- TableQuery[VersionTable].join(TableQuery[ProjectTableMain]).on(_.projectId === _.id)
    if version.createForumPost
    if visibleFilter(project)
  } yield (project, version)

  private val versionTopicFilter = ModelFilter(Version)(_.postId.isEmpty)
  private val versionDirtyFilter = ModelFilter(Version)(_.isPostDirty)

  private val toCreateVersions =
    versionsQueryBase.filter(v => !projectTopicFilter(v._1) && versionTopicFilter(v._2)).to[Vector]
  private val dirtyPostVersions =
    versionsQueryBase
      .filter(v => !projectTopicFilter(v._1) && !versionTopicFilter(v._2) && versionDirtyFilter(v._2))
      .to[Vector]

  /**
    * Starts the recovery task to be run at the specified interval.
    */
  def start(): Unit = {
    this.scheduler.schedule(this.retryRate, this.retryRate, this)
    Logger.info(s"Discourse recovery task started. First run in ${this.retryRate.toString}.")
  }

  override def run(): Unit = {
    import cats.instances.vector._
    Logger.debug("Running Discourse recovery task...")

    def runUpdates[T, M, S[_], A](query: Query[T, M, S], error: String)(use: S[M] => F[A]): Unit =
      service
        .runDBIO(query.result)
        .flatMap { models =>
          use(models)
        }
        .runAsync(TaskUtils.logCallbackNoMDC(error, Logger))
        .unsafeRunSync()

    runUpdates(toCreateProjects, "Failed to create project topic") { toCreate =>
      Logger.debug(s"Creating ${toCreate.size} topics...")
      toCreate.parTraverse(this.api.createProjectTopic)
    }

    runUpdates(dirtyTopicProjects, "Failed to update dirty project") { toUpdate =>
      Logger.debug(s"Updating ${toUpdate.size} topics...")
      toUpdate.parTraverse(this.api.updateProjectTopic)
    }

    runUpdates(toCreateVersions, "Failed to create version post") { toCreate =>
      Logger.debug(s"Creating ${toCreate.size} posts...")
      toCreate.parTraverse(t => this.api.createVersionPost(t._1, t._2))
    }

    runUpdates(dirtyPostVersions, "Failed to update dirty version") { toUpdate =>
      Logger.debug(s"Updating ${toUpdate.size} posts...")
      toUpdate.parTraverse(t => this.api.updateVersionPost(t._1, t._2))
    }

    Logger.debug("Done")
    // TODO: We need to keep deleted projects in case the topic cannot be deleted
  }

}
