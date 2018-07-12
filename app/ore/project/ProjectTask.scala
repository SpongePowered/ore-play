package ore.project

import java.sql.Timestamp
import java.time.Instant
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectSchema
import db.{ModelFilter, ModelService}
import models.project.{Project, VisibilityTypes}
import ore.OreConfig

/**
  * Task that is responsible for publishing New projects
  */
@Singleton
class ProjectTask @Inject()(models: ModelService, actorSystem: ActorSystem, config: OreConfig)(implicit ec: ExecutionContext) extends Runnable {

  val Logger = play.api.Logger("ProjectTask")
  val interval = this.config.projects.get[FiniteDuration]("check-interval")
  val draftExpire: Long = this.config.projects.getOptional[Long]("draft-expire").getOrElse(86400000)

  /**
    * Starts the task.
    */
  def start() = {
    this.actorSystem.scheduler.schedule(this.interval, this.interval, this)
    Logger.info(s"Initialized. First run in ${this.interval.toString}.")
  }

  /**
    * Task runner
    */
  def run() = {
    val actions = this.models.getSchema(classOf[ProjectSchema])

    val newFilter: ModelFilter[Project] = ModelFilter[Project](_.visibility === VisibilityTypes.New)
    val future = actions.collect(newFilter.fn, ProjectSortingStrategies.Default, -1, 0)
    val projects = this.models.await(future).get

    val dayAgo = System.currentTimeMillis() - draftExpire

    projects.foreach(project => {
      Logger.info(s"Found project: ${project.ownerName}/${project.slug}")
      val createdAt = project.createdAt.getOrElse(Timestamp.from(Instant.now())).getTime
      if (createdAt < dayAgo) {
        Logger.info(s"Changed ${project.ownerName}/${project.slug} from New to Public")
        project.setVisibility(VisibilityTypes.Public, "Changed by task", project.ownerId)
      }
    })

  }
}
