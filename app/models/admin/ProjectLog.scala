package models.admin

import java.sql.Timestamp

import db.ModelFilter
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.ProjectLogTable
import db.impl.model.OreModel
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
case class ProjectLog(override val id: Option[Int] = None,
                      override val createdAt: Option[Timestamp] = None,
                      override val projectId: Int)
                      extends OreModel(id, createdAt) with ProjectOwned {

  override type T = ProjectLogTable
  override type M = ProjectLog

  /**
    * Returns all entries in this log.
    *
    * @return Entries in log
    */
  def entries: ModelAccess[ProjectLogEntry] = this.schema.getChildren[ProjectLogEntry](classOf[ProjectLogEntry], this)

  /**
    * Adds a new entry with an "error" tag to the log.
    *
    * @param message  Message to log
    * @return         New entry
    */
  def err(message: String)(implicit ec: ExecutionContext): Future[ProjectLogEntry] = Defined {
    val entries = this.service.access[ProjectLogEntry](
      classOf[ProjectLogEntry], ModelFilter[ProjectLogEntry](_.logId === this.id.get))
    val tag = "error"
    entries.find(e => e.message === message && e.tag === tag).map { entry =>
      entry.setOoccurrences(entry.occurrences + 1)
      entry.setLastOccurrence(this.service.theTime)
      entry
    }.getOrElseF {
      entries.add(ProjectLogEntry(
        logId = this.id.get,
        tag = tag,
        message = message,
        _lastOccurrence = this.service.theTime))
    }
  }

  def copyWith(id: Option[Int], theTime: Option[Timestamp]): ProjectLog = this.copy(id = id, createdAt = theTime)

}
