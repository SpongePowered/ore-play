package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.inject.ApplicationLifecycle

import db.ModelService
import ore.OreConfig
import util.OreMDC

import akka.actor.ActorSystem
import com.typesafe.scalalogging

@Singleton
class DbUpdateTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  val interval: FiniteDuration = config.ore.homepage.updateInterval

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

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
    service.projectBase.refreshHomePage(Logger).unsafeRunSync()
    ()
  }
}
