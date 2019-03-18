package discourse

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import play.api.Logger

import db.ModelFilter._
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectTableMain
import db.{ModelFilter, ModelService}
import models.project.{Project, Visibility}
import ore.OreConfig

import akka.actor.Scheduler

/**
  * Task to periodically retry failed Discourse requests.
  */
class RecoveryTask(scheduler: Scheduler, retryRate: FiniteDuration, api: OreDiscourseApi)(
    implicit ec: ExecutionContext,
    service: ModelService,
    config: OreConfig
) extends Runnable {

  val Logger: Logger = this.api.Logger

  private val topicFilter   = ModelFilter(Project)(_.topicId.isEmpty)
  private val dirtyFilter   = ModelFilter(Project)(_.isTopicDirty)
  private val visibleFilter = Visibility.isPublicFilter[ProjectTableMain]

  private val toCreateProjects   = ModelView.raw(Project).filter(topicFilter && visibleFilter)
  private val dirtyTopicProjects = ModelView.raw(Project).filter(dirtyFilter && visibleFilter)

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

    Logger.debug("Done")
    // TODO: We need to keep deleted projects in case the topic cannot be deleted
  }

}
