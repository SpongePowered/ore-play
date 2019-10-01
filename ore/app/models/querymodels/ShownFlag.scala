package models.querymodels

import java.time.OffsetDateTime

import ore.data.project.{FlagReason, ProjectNamespace}
import ore.db.DbRef
import ore.models.project.{Flag, Visibility}

case class ShownFlag(
    flagId: DbRef[Flag],
    flagCreationDate: OffsetDateTime,
    flagReason: FlagReason,
    flagComment: String,
    reporter: String,
    projectOwnerName: String,
    projectSlug: String,
    projectVisibility: Visibility
) {

  def projectNamespace: ProjectNamespace = ProjectNamespace(projectOwnerName, projectSlug)
}
