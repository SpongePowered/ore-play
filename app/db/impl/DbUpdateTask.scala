package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import db.ModelService
import ore.OreConfig

import akka.actor.ActorSystem
import com.typesafe.scalalogging
import doobie.implicits._

@Singleton
class DbUpdateTask @Inject()(actorSystem: ActorSystem, config: OreConfig)(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  val interval: FiniteDuration = config.ore.homepage.updateInterval

  private val Logger = scalalogging.Logger("DbUpdateTask")

  def start(): Unit = {
    this.actorSystem.scheduler.schedule(interval, interval, this)
    run()
  }

  override def run(): Unit = {
    Logger.debug("Updating homepage view")
    service.runDbCon(sql"REFRESH MATERIALIZED VIEW home_projects".update.run).unsafeToFuture()
    ()
  }
}
