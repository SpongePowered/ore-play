package ore.models.project

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTable, VersionTable}

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{UIO, ZIO, ZSchedule, duration}

/**
  * Task that is responsible for publishing New projects
  */
@Singleton
class ProjectTask @Inject()(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit ec: ExecutionContext,
    service: ModelService[UIO]
) {

  private val Logger = scalalogging.Logger("ProjectTask")

  val interval: duration.Duration = duration.Duration.fromScala(this.config.ore.projects.checkInterval)
  val draftExpire: Long           = this.config.ore.projects.draftExpire.toMillis

  private val dayAgoF =
    ZIO
      .accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS))
      .map(_ - draftExpire)
      .map(Instant.ofEpochMilli)
      .map(OffsetDateTime.ofInstant(_, ZoneOffset.UTC))
  private val newFilter        = ModelFilter(Project)(_.visibility === (Visibility.New: Visibility))
  private val hasVersions      = ModelFilter(Project)(p => TableQuery[VersionTable].filter(_.projectId === p.id).exists)
  private val createdAtFilterF = dayAgoF.map(dayAgo => ModelFilter(Project)(_.createdAt < dayAgo))
  private val updateFalseNewProjects = service.runDBIO(
    TableQuery[ProjectTable].filter(newFilter && hasVersions).map(_.visibility).update(Visibility.Public)
  )

  private val deleteNewProjects =
    createdAtFilterF.flatMap(createdAtFilter => service.deleteWhere(Project)(newFilter && createdAtFilter))

  private val schedule: ZSchedule[Clock, Any, Int] = ZSchedule
    .fixed(interval)
    .logInput(_ => UIO(Logger.debug(s"Deleting draft projects")))

  private val action = (updateFalseNewProjects *> deleteNewProjects).unit.delay(interval)

  //TODO: Repeat in case of failure
  private val task = runtime.unsafeRun(action.option.unit.repeat(schedule).fork)

  lifecycle.addStopHook { () =>
    Future {
      runtime.unsafeRun(task.interrupt)
    }
  }
  Logger.info(s"Initialized. First run in ${this.interval.asScala.toSeconds} seconds.")
}
