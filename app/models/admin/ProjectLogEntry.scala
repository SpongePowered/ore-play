package models.admin

import java.sql.Timestamp

import db.impl.schema.ProjectLogEntryTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjTimestamp}

import slick.lifted.TableQuery

/**
  * Represents an entry in a [[ProjectLog]].
  *
  * @param id               Unique ID
  * @param createdAt        Instant of creation
  * @param logId            ID of log
  * @param tag              Entry tag
  * @param message          Entry message
  * @param occurrences      Amount of occurrences this entry has had
  * @param lastOccurrence   Instant of last occurrence
  */
case class ProjectLogEntry(
    id: ObjId[ProjectLogEntry],
    createdAt: ObjTimestamp,
    logId: DbRef[ProjectLog],
    tag: String,
    message: String,
    occurrences: Int,
    lastOccurrence: Timestamp
) extends Model {

  override type T = ProjectLogEntryTable
  override type M = ProjectLogEntry
}
object ProjectLogEntry {
  def partial(
      logId: DbRef[ProjectLog],
      tag: String,
      message: String,
      occurrences: Int = 1,
      lastOccurrence: Timestamp
  ): InsertFunc[ProjectLogEntry] =
    (id, time) => ProjectLogEntry(id, time, logId, tag, message, occurrences, lastOccurrence)

  implicit val query: ModelQuery[ProjectLogEntry] =
    ModelQuery.from[ProjectLogEntry](TableQuery[ProjectLogEntryTable], _.copy(_, _))
}
