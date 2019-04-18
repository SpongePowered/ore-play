package ore.db.impl.schema

import java.time.Instant

import ore.db.impl.OrePostgresDriver.api._
import ore.models.admin.{ProjectLog, ProjectLogEntry}
import ore.db.DbRef

class ProjectLogEntryTable(tg: Tag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def logId          = column[DbRef[ProjectLog]]("log_id")
  def tag            = column[String]("tag")
  def message        = column[String]("message")
  def occurrences    = column[Int]("occurrences")
  def lastOccurrence = column[Instant]("last_occurrence")

  override def * =
    (id.?, createdAt.?, (logId, tag, message, occurrences, lastOccurrence)) <> (mkApply(
      (ProjectLogEntry.apply _).tupled
    ), mkUnapply(ProjectLogEntry.unapply))
}
