package ore.models.project

import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.inject.ApplicationLifecycle

import ore.db.impl.OrePostgresDriver.api._
import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.schema.{ProjectTableMain, VersionTable}
import util.IOUtils

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging

/**
  * Task that is responsible for publishing New projects
  */
@Singleton
class ProjectTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    service: ModelService[IO]
) extends Runnable {

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  private val Logger = scalalogging.Logger("ProjectTask")

  val interval: FiniteDuration = this.config.ore.projects.checkInterval
  val draftExpire: Long        = this.config.ore.projects.draftExpire.toMillis

  private def dayAgo          = Instant.ofEpochMilli(System.currentTimeMillis() - draftExpire)
  private val newFilter       = ModelFilter(Project)(_.visibility === (Visibility.New: Visibility))
  private val hasVersions     = ModelFilter(Project)(p => TableQuery[VersionTable].filter(_.projectId === p.id).exists)
  private def createdAtFilter = ModelFilter(Project)(_.createdAt < dayAgo)
  private val updateFalseNewProjects = service.runDBIO(
    TableQuery[ProjectTableMain].filter(newFilter && hasVersions).map(_.visibility).update(Visibility.Public)
  )
  private def deleteNewProjects = service.deleteWhere(Project)(newFilter && createdAtFilter)

  /**
    * Starts the task.
    */
  def start(): Unit = {
    val task = this.actorSystem.scheduler.schedule(this.interval, this.interval, this)
    lifecycle.addStopHook { () =>
      Future {
        task.cancel()
      }
    }
    Logger.info(s"Initialized. First run in ${this.interval.toString}.")
  }

  /**
    * Task runner
    */
  def run(): Unit = {
    Logger.debug(s"Deleting draft projects")
    updateFalseNewProjects.unsafeRunAsync(IOUtils.logCallbackUnitNoMDC("Update false new project failed", Logger))
    deleteNewProjects.unsafeRunAsync(IOUtils.logCallbackUnitNoMDC("Delete new project failed", Logger))
  }
}
