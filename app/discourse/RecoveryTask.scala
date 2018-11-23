package discourse

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import play.api.Logger

import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.impl.access.ProjectBase
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

  val projects = ProjectBase()

  private val topicFilter = ModelFilter[Project](_.topicId.isEmpty)
  private val dirtyFilter = ModelFilter[Project](_.isTopicDirty)

  private val toCreateProjects   = this.projects.filter(topicFilter && Visibility.isPublicFilter)
  private val dirtyTopicProjects = this.projects.filter(dirtyFilter && Visibility.isPublicFilter)

  /**
    * Starts the recovery task to be run at the specified interval.
    */
  def start(): Unit = {
    this.scheduler.schedule(this.retryRate, this.retryRate, this)
    Logger.info(s"Discourse recovery task started. First run in ${this.retryRate.toString}.")
  }

  override def run(): Unit = {
    Logger.debug("Running Discourse recovery task...")

    toCreateProjects.unsafeToFuture().foreach { toCreate =>
      Logger.debug(s"Creating ${toCreate.size} topics...")
      toCreate.foreach(this.api.createProjectTopic)
    }

    dirtyTopicProjects.unsafeToFuture().foreach { toUpdate =>
      Logger.debug(s"Updating ${toUpdate.size} topics...")
      toUpdate.foreach(this.api.updateProjectTopic)
    }

    Logger.debug("Done")
    // TODO: We need to keep deleted projects in case the topic cannot be deleted
  }

}
