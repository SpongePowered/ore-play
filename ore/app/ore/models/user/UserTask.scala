package ore.models.user

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import ore.db.impl.OrePostgresDriver.api._
import ore.OreConfig
import ore.db.ModelService
import ore.models.api.ApiSession
import ore.util.OreMDC

import com.typesafe.scalalogging
import zio.{UIO, ZIO, ZSchedule}
import zio.clock.Clock
import zio.duration.Duration

@Singleton
class UserTask @Inject()(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit ec: ExecutionContext,
    service: ModelService[UIO]
) {

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("UserTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  val interval: Duration = zio.duration.Duration.fromScala(config.ore.api.session.checkInterval)

  private val action = ZIO
    .accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS))
    .map(Instant.ofEpochMilli)
    .flatMap(now => service.deleteWhere(ApiSession)(_.expires < now))
    .unit

  private val schedule: ZSchedule[Clock, Any, Int] = ZSchedule.fixed(interval)

  Logger.info("DbUpdateTask starting")
  //TODO: Repeat in case of failure
  private val task = runtime.unsafeRun(action.option.unit.repeat(schedule).fork)

  lifecycle.addStopHook { () =>
    Future {
      runtime.unsafeRun(task.interrupt)
    }
  }
}
