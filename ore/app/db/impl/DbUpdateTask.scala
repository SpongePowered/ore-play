package db.impl

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import db.impl.access.ProjectBase
import ore.OreConfig
import ore.util.OreMDC

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{UIO, ZSchedule, duration}

@Singleton
class DbUpdateTask @Inject()(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit ec: ExecutionContext,
    projects: ProjectBase[UIO]
) {

  val interval: duration.Duration = duration.Duration.fromScala(config.ore.homepage.updateInterval)

  private val Logger               = scalalogging.Logger.takingImplicit[OreMDC]("DbUpdateTask")
  implicit private val mdc: OreMDC = OreMDC.NoMDC

  Logger.info("DbUpdateTask starting")

  private val schedule: ZSchedule[Clock, Any, Int] = ZSchedule
    .fixed(interval)
    .logInput(_ => UIO(Logger.debug(s"Updating homepage view")))

  private val task = runtime.unsafeRun(projects.refreshHomePage(Logger).option.unit.repeat(schedule).fork)
  lifecycle.addStopHook { () =>
    Future {
      runtime.unsafeRun(task.interrupt)
    }
  }
}
