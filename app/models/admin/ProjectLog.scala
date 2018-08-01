package models.admin

import db.{Model, ModelFilter, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.ProjectLogTable
import ore.project.ProjectOwned
import util.instances.future._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a log for a [[models.project.Project]].
  *
  * @param id         Log ID
  * @param createdAt  Instant of creation
  * @param projectId  ID of project log is for
  */
case class ProjectLog(id: ObjectId = ObjectId.Uninitialized,
                      createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                      projectId: ObjectReference) extends Model with ProjectOwned {

  override type T = ProjectLogTable
  override type M = ProjectLog

  /**
    * Returns all entries in this log.
    *
    * @return Entries in log
    */
  def entries(implicit service: ModelService): ModelAccess[ProjectLogEntry] = this.schema.getChildren[ProjectLogEntry](classOf[ProjectLogEntry], this)

  /**
    * Adds a new entry with an "error" tag to the log.
    *
    * @param message  Message to log
    * @return         New entry
    */
  def err(message: String)(implicit ec: ExecutionContext, service: ModelService): Future[ProjectLogEntry] = Defined {
    val entries = service.access[ProjectLogEntry](
      classOf[ProjectLogEntry], ModelFilter[ProjectLogEntry](_.logId === this.id.value))
    val tag = "error"
    entries.find(e => e.message === message && e.tag === tag).semiFlatMap { entry =>
      entries.update(entry.copy(
        occurrences = entry.occurrences + 1,
        lastOccurrence = service.theTime
      ))
    }.getOrElseF {
      entries.add(ProjectLogEntry(
        logId = this.id.value,
        tag = tag,
        message = message,
        lastOccurrence = service.theTime))
    }
  }

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectLog = this.copy(id = id, createdAt = theTime)
}
