package ore.models.user

import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import play.api.inject.ApplicationLifecycle

import ore.db.impl.OrePostgresDriver.api._
import ore.OreConfig
import ore.db.ModelService
import ore.models.api.ApiSession
import ore.util.OreMDC

import akka.actor.ActorSystem
import cats.effect.IO
import com.typesafe.scalalogging

@Singleton
class UserTask @Inject()(actorSystem: ActorSystem, config: OreConfig, lifecycle: ApplicationLifecycle)(
    implicit ec: ExecutionContext,
    service: ModelService[IO]
) extends Runnable {

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("UserTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  val interval: FiniteDuration = config.ore.api.session.checkInterval

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
    val now = Instant.now()
    service.deleteWhere(ApiSession)(_.expires < now).unsafeRunAsyncAndForget()
  }
}
