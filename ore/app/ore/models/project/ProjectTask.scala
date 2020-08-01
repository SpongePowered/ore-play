package ore.models.project

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import play.api.inject.ApplicationLifecycle

import ore.OreConfig
import ore.db.ModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTable, VersionTable}

import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{Schedule, UIO, duration}

/**
  * Task that is responsible for publishing New projects
  */
class ProjectTask(config: OreConfig, lifecycle: ApplicationLifecycle, runtime: zio.Runtime[Clock])(
    implicit service: ModelService[UIO]
) {

  private val Logger = scalalogging.Logger("ProjectTask")

  val interval: duration.Duration = duration.Duration.fromScala(this.config.ore.projects.checkInterval)
  val draftExpire: Long           = this.config.ore.projects.draftExpire.toMillis

  private val dayAgoF =
    zio.clock
      .currentTime(TimeUnit.MILLISECONDS)
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

  private val schedule: Schedule[Clock, Any, Int] = Schedule
    .fixed(interval)
    .tapInput(_ => UIO(Logger.debug(s"Deleting draft projects")))

  private val action = (updateFalseNewProjects *> deleteNewProjects).unit.delay(interval)

  private val task = runtime.unsafeRunToFuture(
    action.catchAllCause(cause => UIO.effectTotal(Logger.error(cause.prettyPrint))).repeat(schedule)
  )

  lifecycle.addStopHook(() => task.cancel())
  Logger.info(s"Initialized. First run in ${this.interval.asScala.toSeconds} seconds.")
}
