package db.impl.schema

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import models.admin.{ProjectLog, ProjectLogEntry}
import ore.db.DbRef

class ProjectLogEntryTable(tg: Tag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def logId          = column[DbRef[ProjectLog]]("log_id")
  def tag            = column[String]("tag")
  def message        = column[String]("message")
  def occurrences    = column[Int]("occurrences")
  def lastOccurrence = column[Timestamp]("last_occurrence")

  override def * =
    (id.?, createdAt.?, (logId, tag, message, occurrences, lastOccurrence)) <> (mkApply(
      (ProjectLogEntry.apply _).tupled
    ), mkUnapply(ProjectLogEntry.unapply))
}
