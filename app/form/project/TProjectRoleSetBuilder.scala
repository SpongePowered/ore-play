package form.project

import form.RoleSetBuilder
import models.user.role.ProjectRole
import ore.permission.role.RoleType

/**
  * Takes form data and builds an uninitialized set of [[ProjectRole]].
  */
trait TProjectRoleSetBuilder extends RoleSetBuilder[ProjectRole] {

  override def newRole(userId: Int, role: RoleType) = new ProjectRole(userId, role, -1, false, true)

}
