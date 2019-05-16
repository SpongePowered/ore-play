package models.querymodels

import ore.data.project.{FlagReason, ProjectNamespace}
import ore.models.project.{Flag, Visibility}
import ore.db.DbRef

case class ShownFlag(
    flagId: DbRef[Flag],
    flagReason: FlagReason,
    flagComment: String,
    reporter: String,
    projectOwnerName: String,
    projectSlug: String,
    projectVisibility: Visibility
) {

  def projectNamespace: ProjectNamespace = ProjectNamespace(projectOwnerName, projectSlug)
}
