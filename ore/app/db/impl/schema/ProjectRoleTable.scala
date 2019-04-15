package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.RoleTable
import models.project.Project
import models.user.role.ProjectUserRole
import ore.db.DbRef

class ProjectRoleTable(tag: Tag)
    extends ModelTable[ProjectUserRole](tag, "user_project_roles")
    with RoleTable[ProjectUserRole] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * =
    (id.?, createdAt.?, (userId, projectId, roleType, isAccepted)) <> (mkApply((ProjectUserRole.apply _).tupled), mkUnapply(
      ProjectUserRole.unapply
    ))
}
