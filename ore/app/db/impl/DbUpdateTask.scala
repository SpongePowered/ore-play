package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import db.impl.access.ProjectBase
import ore.OreConfig
import ore.util.OreMDC

import akka.actor.ActorSystem
import cats.effect.IO
import com.typesafe.scalalogging

@Singleton
class DbUpdateTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    projects: ProjectBase[IO]
) extends Runnable {

  val interval: FiniteDuration = config.ore.homepage.updateInterval

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  Logger.info("DbUpdateTask starting")
  private val task = this.actorSystem.scheduler.schedule(interval, interval, this)
  lifecycle.addStopHook { () =>
    Future {
      task.cancel()
    }
  }
  run()

  override def run(): Unit = {
    Logger.debug("Updating homepage view")
    projects.refreshHomePage(Logger).unsafeRunSync()
    ()
  }
}
