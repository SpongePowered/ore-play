package models.admin

import java.sql.Timestamp

import db.impl.schema.ProjectLogEntryTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}

import slick.lifted.TableQuery

/**
  * Represents an entry in a [[ProjectLog]].
  *
  * @param logId            ID of log
  * @param tag              Entry tag
  * @param message          Entry message
  * @param occurrences      Amount of occurrences this entry has had
  * @param lastOccurrence   Instant of last occurrence
  */
case class ProjectLogEntry(
    logId: DbRef[ProjectLog],
    tag: String,
    message: String,
    occurrences: Int = 1,
    lastOccurrence: Timestamp
)
object ProjectLogEntry
    extends DefaultModelCompanion[ProjectLogEntry, ProjectLogEntryTable](TableQuery[ProjectLogEntryTable]) {

  implicit val query: ModelQuery[ProjectLogEntry] =
    ModelQuery.from(this)
}
