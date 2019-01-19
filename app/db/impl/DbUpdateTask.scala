package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.inject.ApplicationLifecycle

import db.ModelService
import ore.OreConfig

import akka.actor.ActorSystem
import com.typesafe.scalalogging
import doobie.implicits._

@Singleton
class DbUpdateTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  val interval: FiniteDuration = config.ore.homepage.updateInterval

  private val Logger = scalalogging.Logger("DbUpdateTask")

  def start(): Unit = {
    Logger.info("DbUpdateTask starting")
    val task = this.actorSystem.scheduler.schedule(interval, interval, this)
    lifecycle.addStopHook { () =>
      Future {
        task.cancel()
      }
    }
    run()
  }

  override def run(): Unit = {
    Logger.debug("Updating homepage view")
    service.runDbCon(sql"REFRESH MATERIALIZED VIEW home_projects".update.run).unsafeToFuture()
    ()
  }
}
