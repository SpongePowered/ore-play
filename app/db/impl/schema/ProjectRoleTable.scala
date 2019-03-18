package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.RoleTable
import db.table.ModelTable
import models.project.Project
import models.user.role.ProjectUserRole

class ProjectRoleTable(tag: Tag)
    extends ModelTable[ProjectUserRole](tag, "user_project_roles")
    with RoleTable[ProjectUserRole] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * =
    (id.?, createdAt.?, (userId, projectId, roleType, isAccepted)) <> (mkApply((ProjectUserRole.apply _).tupled), mkUnapply(
      ProjectUserRole.unapply
    ))
}
