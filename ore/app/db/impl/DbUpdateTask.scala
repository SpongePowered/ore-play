package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.inject.ApplicationLifecycle

import db.impl.access.ProjectBase
import db.impl.query.StatTrackerQueries
import ore.OreConfig
import ore.db.ModelService
import ore.util.OreMDC

import cats.syntax.all._

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{UIO, ZSchedule, duration}

@Singleton
class DbUpdateTask @Inject()(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit ec: ExecutionContext,
    projects: ProjectBase[UIO],
    service: ModelService[UIO]
) {

  val interval: duration.Duration = duration.Duration.fromScala(config.ore.homepage.updateInterval)

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  Logger.info("DbUpdateTask starting")

  private val homepageSchedule: ZSchedule[Clock, Any, Int] = ZSchedule
    .fixed(interval)
    .logInput(_ => UIO(Logger.debug(s"Updating homepage view")))

  private val statSchedule: ZSchedule[Clock, Any, Int] =
    ZSchedule.fixed(duration.Duration.fromScala(1.day)).logInput(_ => UIO(Logger.debug("Processing stats")))

  private val homepageTask =
    runtime.unsafeRun(projects.refreshHomePage(Logger).option.unit.repeat(homepageSchedule).fork)
  private val statsTask = runtime.unsafeRun(
    service
      .runDbCon(StatTrackerQueries.processProjectViews.run *> StatTrackerQueries.processVersionDownloads.run)
      .option
      .unit
      .repeat(statSchedule)
      .fork
  )
  lifecycle.addStopHook { () =>
    Future {
      runtime.unsafeRun(homepageTask.interrupt)
      runtime.unsafeRun(statsTask.interrupt)
    }
  }
}
