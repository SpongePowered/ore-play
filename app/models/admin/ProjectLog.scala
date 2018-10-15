package models.admin

import scala.concurrent.{ExecutionContext, Future}

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectLogTable
import db.{Model, ModelFilter, ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.project.ProjectOwned

import cats.instances.future._
import slick.lifted.TableQuery

/**
  * Represents a log for a [[models.project.Project]].
  *
  * @param id         Log ID
  * @param createdAt  Instant of creation
  * @param projectId  ID of project log is for
  */
case class ProjectLog(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: ObjectReference
) extends Model
    with ProjectOwned {

  override type T = ProjectLogTable
  override type M = ProjectLog

  /**
    * Returns all entries in this log.
    *
    * @return Entries in log
    */
  def entries(implicit service: ModelService): ModelAccess[ProjectLogEntry] =
    service.access[ProjectLogEntry](ModelFilter(_.logId === id.value))

  /**
    * Adds a new entry with an "error" tag to the log.
    *
    * @param message  Message to log
    * @return         New entry
    */
  def err(message: String)(implicit ec: ExecutionContext, service: ModelService): Future[ProjectLogEntry] = Defined {
    val tag = "error"
    entries
      .find(e => e.message === message && e.tag === tag)
      .semiflatMap { entry =>
        entries.update(
          entry.copy(
            occurrences = entry.occurrences + 1,
            lastOccurrence = service.theTime
          )
        )
      }
      .getOrElseF {
        entries
          .add(ProjectLogEntry(logId = this.id.value, tag = tag, message = message, lastOccurrence = service.theTime))
      }
  }

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectLog = this.copy(id = id, createdAt = theTime)
}
object ProjectLog {
  implicit val query: ModelQuery[ProjectLog] =
    ModelQuery.from[ProjectLog](TableQuery[ProjectLogTable])
}
