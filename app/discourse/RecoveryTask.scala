package discourse

import akka.actor.Scheduler
import db.impl.OrePostgresDriver.api._
import db.impl.access.ProjectBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

/**
  * Task to periodically retry failed Discourse requests.
  */
class RecoveryTask(scheduler: Scheduler,
                   retryRate: FiniteDuration,
                   api: OreDiscourseApi,
                   projects: ProjectBase) extends Runnable {

  val Logger = this.api.Logger

  /**
    * Starts the recovery task to be run at the specified interval.
    */
  def start() = {
    this.scheduler.schedule(this.retryRate, this.retryRate, this)
    Logger.info(s"Discourse recovery task started. First run in ${this.retryRate.toString}.")
  }

  override def run() = {
    Logger.info("Running Discourse recovery task...")

    this.projects.filter(_.topicId === -1).foreach { toCreate =>
      Logger.info(s"Creating ${toCreate.size} topics...")
      toCreate.foreach(this.api.createProjectTopic)
    }

    this.projects.filter(_.isTopicDirty).foreach { toUpdate =>
      Logger.info(s"Updating ${toUpdate.size} topics...")
      toUpdate.foreach(this.api.updateProjectTopic)
    }

    Logger.info("Done")
    // TODO: We need to keep deleted projects in case the topic cannot be deleted
  }

}
