package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.RoleTable
import db.table.ModelTable
import models.project.Project
import models.user.role.ProjectRole

class ProjectRoleTable(tag: RowTag)
    extends ModelTable[ProjectRole](tag, "user_project_roles")
    with RoleTable[ProjectRole] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * = mkProj((id.?, createdAt.?, userId, projectId, roleType, isAccepted))(mkTuple[ProjectRole]())
}
