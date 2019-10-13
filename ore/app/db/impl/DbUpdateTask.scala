package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import db.impl.access.ProjectBase
import db.impl.query.StatTrackerQueries
import ore.OreConfig
import ore.db.ModelService
import ore.util.OreMDC

import cats.syntax.all._
import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{Task, UIO, ZIO, ZSchedule, duration}

@Singleton
class DbUpdateTask @Inject()(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit ec: ExecutionContext,
    projects: ProjectBase[Task],
    service: ModelService[Task]
) {

  val interval: duration.Duration = duration.Duration.fromScala(config.ore.homepage.updateInterval)

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  Logger.info("DbUpdateTask starting")

  private val homepageSchedule: ZSchedule[Clock, Any, Int] = ZSchedule
    .fixed(interval)
    .logInput(_ => UIO(Logger.debug(s"Updating homepage view")))

  private val statSchedule: ZSchedule[Clock, Any, Int] =
    ZSchedule
      .fixed(interval)
      .logInput(_ => UIO(Logger.debug("Processing stats")))

  private def runningTask(task: Task[Unit], schedule: ZSchedule[Clock, Any, Int]) = {
    val safeTask: ZIO[Any, Unit, Unit] = task.flatMapError(e => UIO(Logger.error("Running DB task failed", e)))

    runtime.unsafeRun(safeTask.repeat(schedule).fork)
  }

  private val homepageTask = runningTask(projects.refreshHomePage(Logger), homepageSchedule)
  private val statsTask = runningTask(
    service
      .runDbCon(StatTrackerQueries.processProjectViews.unique *> StatTrackerQueries.processVersionDownloads.unique)
      .unit,
    statSchedule
  )
  lifecycle.addStopHook { () =>
    Future {
      runtime.unsafeRun(homepageTask.interrupt)
      runtime.unsafeRun(statsTask.interrupt)
    }
  }
}
