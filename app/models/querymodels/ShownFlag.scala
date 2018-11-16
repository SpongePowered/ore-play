package models.querymodels

import db.DbRef
import models.project.{Flag, Visibility}
import ore.permission.role.{Role, Trust}
import ore.project.FlagReason

case class ShownFlag(
    flagId: DbRef[Flag],
    flagReason: FlagReason,
    flagComment: String,
    reporter: String,
    projectOwnerName: String,
    projectSlug: String,
    projectVisibility: Visibility,
    requesterRoles: List[Role],
    requesterTrust: Trust
) {

  def projectNamespace: ProjectNamespace = ProjectNamespace(projectOwnerName, projectSlug)
}
