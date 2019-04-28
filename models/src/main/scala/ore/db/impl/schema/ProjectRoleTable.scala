package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.RoleTable
import ore.models.project.Project
import ore.models.user.role.ProjectUserRole

class ProjectRoleTable(tag: Tag)
    extends ModelTable[ProjectUserRole](tag, "user_project_roles")
    with RoleTable[ProjectUserRole] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * =
    (id.?, createdAt.?, (userId, projectId, roleType, isAccepted)) <> (mkApply((ProjectUserRole.apply _).tupled), mkUnapply(
      ProjectUserRole.unapply
    ))
}
