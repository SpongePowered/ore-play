package ore.project

import java.sql.Timestamp
import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelService}
import models.project.{Project, Visibility}
import ore.OreConfig

import akka.actor.ActorSystem

/**
  * Task that is responsible for publishing New projects
  */
@Singleton
class ProjectTask @Inject()(actorSystem: ActorSystem, config: OreConfig)(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  val Logger                   = play.api.Logger("ProjectTask")
  val interval: FiniteDuration = this.config.projects.get[FiniteDuration]("check-interval")
  val draftExpire: Long        = this.config.projects.getOptional[Long]("draft-expire").getOrElse(86400000)

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
  def run(): Unit = {
    val dayAgo          = Timestamp.from(Instant.ofEpochMilli(System.currentTimeMillis() - draftExpire))
    val newFilter       = ModelFilter[Project](_.visibility === (Visibility.New: Visibility))
    val createdAtFilter = ModelFilter[Project](_.createdAt < dayAgo)
    val future          = service.filter[Project](newFilter && createdAtFilter)

    future.foreach { projects =>
      projects.foreach { project =>
        Logger.debug(s"Changed ${project.ownerName}/${project.slug} from New to Public")
        project.setVisibility(Visibility.Public, "Changed by task", project.ownerId)
      }
    }
  }
}
