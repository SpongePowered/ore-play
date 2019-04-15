package ore.user

import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import play.api.inject.ApplicationLifecycle

import db.impl.OrePostgresDriver.api._
import models.api.ApiSession
import ore.OreConfig
import ore.db.ModelService
import util.OreMDC

import akka.actor.ActorSystem
import com.typesafe.scalalogging

@Singleton
class UserTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("UserTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  val interval: FiniteDuration = config.ore.api.session.checkInterval

  def start(): Unit = {
    Logger.info("DbUpdateTask starting")
    this.actorSystem.scheduler.schedule(interval, interval, this)
    run()
  }

  override def run(): Unit = {
    val now = Instant.now()
    service.deleteWhere(ApiSession)(_.expires < now).unsafeRunAsyncAndForget()
  }
}
