package models.querymodels

import ore.models.project.{Flag, Visibility}
import ore.db.DbRef
import ore.models.project.FlagReason

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
