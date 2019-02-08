package models.admin

import scala.language.higherKinds

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectLogTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjTimestamp}
import models.project.Project
import ore.project.ProjectOwned
import util.syntax._

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a log for a [[models.project.Project]].
  *
  * @param id         Log ID
  * @param createdAt  Instant of creation
  * @param projectId  ID of project log is for
  */
case class ProjectLog private (
    id: ObjId[ProjectLog],
    createdAt: ObjTimestamp,
    projectId: DbRef[Project]
) extends Model {

  override type T = ProjectLogTable
  override type M = ProjectLog

  /**
    * Returns all entries in this log.
    *
    * @return Entries in log
    */
  def entries[V[_, _]: QueryView](view: V[ProjectLogEntry#T, ProjectLogEntry]): V[ProjectLogEntry#T, ProjectLogEntry] =
    view.filterView(_.logId === id.value)

  /**
    * Adds a new entry with an "error" tag to the log.
    *
    * @param message  Message to log
    * @return         New entry
    */
  def err(message: String)(implicit service: ModelService): IO[ProjectLogEntry] = {
    val tag = "error"
    entries(ModelView.now[ProjectLogEntry])
      .find(e => e.message === message && e.tag === tag)
      .semiflatMap { entry =>
        service.update(
          entry.copy(
            occurrences = entry.occurrences + 1,
            lastOccurrence = service.theTime
          )
        )
      }
      .getOrElseF {
        service.insert(
          ProjectLogEntry.partial(id, tag, message, lastOccurrence = service.theTime)
        )
      }
  }
}
object ProjectLog {
  def partial(projectId: DbRef[Project]): InsertFunc[ProjectLog] = (id, time) => ProjectLog(id, time, projectId)

  implicit val query: ModelQuery[ProjectLog] =
    ModelQuery.from[ProjectLog](TableQuery[ProjectLogTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[ProjectLog] = (a: ProjectLog) => a.projectId
}
