package ore.models.user

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import play.api.inject.ApplicationLifecycle

import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.models.api.ApiSession
import ore.util.OreMDC

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.duration.Duration
import zio.{Schedule, UIO, ZIO}

class UserTask(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit service: ModelService[UIO]
) {

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("UserTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  val interval: Duration = zio.duration.Duration.fromScala(config.ore.api.session.checkInterval)

  private val action = zio.clock
    .currentTime(TimeUnit.MILLISECONDS)
    .map(Instant.ofEpochMilli)
    .map(OffsetDateTime.ofInstant(_, ZoneOffset.UTC))
    .flatMap(now => service.deleteWhere(ApiSession)(_.expires < now))
    .unit

  private val schedule: Schedule[Clock, Any, Int] =
    Schedule.fixed(interval).tapInput(_ => UIO(Logger.debug(s"Deleting old API sessions")))

  Logger.info("DbUpdateTask starting")
  private val task = runtime.unsafeRunToFuture(
    action.catchAllCause(cause => UIO.effectTotal(Logger.error(cause.prettyPrint))).repeat(schedule)
  )

  lifecycle.addStopHook(() => task.cancel())
}
