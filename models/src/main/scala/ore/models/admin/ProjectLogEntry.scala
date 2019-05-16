package ore.models.admin

import java.time.Instant

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.ProjectLogEntryTable
import ore.db.{DbRef, ModelQuery}
import ore.models.project.Project

import slick.lifted.TableQuery

/**
  * Represents an entry in a [[ProjectLog]].
  *
  * @param projectId        ID of project this belongs to
  * @param tag              Entry tag
  * @param message          Entry message
  * @param occurrences      Amount of occurrences this entry has had
  * @param lastOccurrence   Instant of last occurrence
  */
case class ProjectLogEntry(
    projectId: DbRef[Project],
    tag: String,
    message: String,
    occurrences: Int = 1,
    lastOccurrence: Instant
)
object ProjectLogEntry
    extends DefaultModelCompanion[ProjectLogEntry, ProjectLogEntryTable](TableQuery[ProjectLogEntryTable]) {

  implicit val query: ModelQuery[ProjectLogEntry] =
    ModelQuery.from(this)
}
