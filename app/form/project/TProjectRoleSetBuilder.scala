package form.project

import db.DbRef
import form.RoleSetBuilder
import models.user.User
import models.user.role.ProjectRole
import ore.permission.role.RoleType

/**
  * Takes form data and builds an uninitialized set of [[ProjectRole]].
  */
trait TProjectRoleSetBuilder extends RoleSetBuilder[ProjectRole] {

  override def newRole(userId: DbRef[User], role: RoleType) = new ProjectRole(userId, role, -1L, false, true)

}
