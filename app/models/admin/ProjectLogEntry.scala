package models.admin

import java.sql.Timestamp

import db.impl.schema.ProjectLogEntryTable
import db.{DbRef, Model, ModelQuery, ObjId, ObjectTimestamp}

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
    id: ObjId[ProjectLogEntry] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    logId: DbRef[ProjectLog],
    tag: String,
    message: String,
    occurrences: Int = 1,
    lastOccurrence: Timestamp
) extends Model {

  override type T = ProjectLogEntryTable
  override type M = ProjectLogEntry
}
object ProjectLogEntry {
  implicit val query: ModelQuery[ProjectLogEntry] =
    ModelQuery.from[ProjectLogEntry](TableQuery[ProjectLogEntryTable], _.copy(_, _))
}
