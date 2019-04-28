package discourse

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTableMain, VersionTable}
import ore.models.project.{Project, Version, Visibility}
import ore.OreConfig
import ore.db.ModelService
import ore.db.access.ModelView

import akka.actor.Scheduler
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging

/**
  * Task to periodically retry failed Discourse requests.
  */
class RecoveryTask(scheduler: Scheduler, retryRate: FiniteDuration, api: OreDiscourseApi)(
    implicit ec: ExecutionContext,
    service: ModelService[IO],
    config: OreConfig
) extends Runnable {

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  val Logger: scalalogging.Logger = this.api.Logger

  private val projectTopicFilter = ModelFilter(Project)(_.topicId.isEmpty)
  private val projectDirtyFilter = ModelFilter(Project)(_.isTopicDirty)
  private val visibleFilter      = Visibility.isPublicFilter[ProjectTableMain]

  private val toCreateProjects   = ModelView.raw(Project).filter(projectTopicFilter && visibleFilter)
  private val dirtyTopicProjects = ModelView.raw(Project).filter(projectDirtyFilter && visibleFilter)

  private val versionsQueryBase = for {
    (version, project) <- TableQuery[VersionTable].join(TableQuery[ProjectTableMain]).on(_.projectId === _.id)
    if version.createForumPost
    if visibleFilter(project)
  } yield (project, version)

  private val versionTopicFilter = ModelFilter(Version)(_.postId.isEmpty)
  private val versionDirtyFilter = ModelFilter(Version)(_.isPostDirty)

  private val toCreateVersions  = versionsQueryBase.filter(v => versionTopicFilter(v._2))
  private val dirtyPostVersions = versionsQueryBase.filter(v => versionDirtyFilter(v._2))

  /**
    * Starts the recovery task to be run at the specified interval.
    */
  def start(): Unit = {
    this.scheduler.schedule(this.retryRate, this.retryRate, this)
    Logger.info(s"Discourse recovery task started. First run in ${this.retryRate.toString}.")
  }

  override def run(): Unit = {
    Logger.debug("Running Discourse recovery task...")

    service.runDBIO(toCreateProjects.result).unsafeToFuture().foreach { toCreate =>
      Logger.debug(s"Creating ${toCreate.size} topics...")
      toCreate.foreach(this.api.createProjectTopic(_).unsafeToFuture())
    }

    service.runDBIO(dirtyTopicProjects.result).unsafeToFuture().foreach { toUpdate =>
      Logger.debug(s"Updating ${toUpdate.size} topics...")
      toUpdate.foreach(this.api.updateProjectTopic(_).unsafeToFuture())
    }

    service.runDBIO(toCreateVersions.result).unsafeToFuture().foreach { toCreate =>
      Logger.debug(s"Creating ${toCreate.size} posts...")
      toCreate.foreach(t => this.api.createVersionPost(t._1, t._2).unsafeToFuture())
    }

    service.runDBIO(dirtyPostVersions.result).unsafeToFuture().foreach { toUpdate =>
      Logger.debug(s"Updating ${toUpdate.size} posts...")
      toUpdate.foreach(t => this.api.updateVersionPost(t._1, t._2).unsafeToFuture())
    }

    Logger.debug("Done")
    // TODO: We need to keep deleted projects in case the topic cannot be deleted
  }

}
