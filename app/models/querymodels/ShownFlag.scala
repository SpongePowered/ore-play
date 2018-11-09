package models.querymodels

import db.ObjectReference
import models.project.Visibility
import ore.permission.role.{Role, Trust}
import ore.project.FlagReason

case class ShownFlag(
    flagId: ObjectReference,
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
