package ore.models.project

import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import ore.db.impl.OrePostgresDriver.api._
import ore.OreConfig
import ore.db.ModelService
import ore.db.access.ModelView
import util.syntax._

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging

/**
  * Task that is responsible for publishing New projects
  */
@Singleton
class ProjectTask @Inject()(actorSystem: ActorSystem, config: OreConfig)(
    implicit ec: ExecutionContext,
    service: ModelService[IO]
) extends Runnable {

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  private val Logger = scalalogging.Logger("ProjectTask")

  val interval: FiniteDuration = this.config.ore.projects.checkInterval
  val draftExpire: Long        = this.config.ore.projects.draftExpire.toMillis

  private def dayAgo          = Instant.ofEpochMilli(System.currentTimeMillis() - draftExpire)
  private val newFilter       = ModelFilter(Project)(_.visibility === (Visibility.New: Visibility))
  private def createdAtFilter = ModelFilter(Project)(_.createdAt < dayAgo)
  private def newProjects     = service.runDBIO(ModelView.now(Project).query.filter(newFilter && createdAtFilter).result)

  /**
    * Starts the task.
    */
  def start(): Unit = {
    this.actorSystem.scheduler.schedule(this.interval, this.interval, this)
    Logger.info(s"Initialized. First run in ${this.interval.toString}.")
  }

  /**
    * Task runner
    */
  def run(): Unit = newProjects.unsafeToFuture().foreach { projects =>
    projects.foreach { project =>
      Logger.debug(s"Changed ${project.ownerName}/${project.slug} from New to Public")
      project.setVisibility(Visibility.Public, "Changed by task", project.ownerId).unsafeRunAsyncAndForget()
    }
  }
}
