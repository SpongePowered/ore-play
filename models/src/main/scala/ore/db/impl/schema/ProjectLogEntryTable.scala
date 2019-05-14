package ore.db.impl.schema

import java.time.Instant

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.admin.ProjectLogEntry
import ore.models.project.Project

class ProjectLogEntryTable(tg: Tag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def projectId      = column[DbRef[Project]]("project_id")
  def tag            = column[String]("tag")
  def message        = column[String]("message")
  def occurrences    = column[Int]("occurrences")
  def lastOccurrence = column[Instant]("last_occurrence")

  override def * =
    (id.?, createdAt.?, (projectId, tag, message, occurrences, lastOccurrence)) <> (mkApply(
      (ProjectLogEntry.apply _).tupled
    ), mkUnapply(ProjectLogEntry.unapply))
}
