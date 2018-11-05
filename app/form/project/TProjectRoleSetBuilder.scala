package form.project

import db.ObjectReference
import form.RoleSetBuilder
import models.user.role.ProjectUserRole
import ore.permission.role.Role

/**
  * Takes form data and builds an uninitialized set of [[ProjectUserRole]].
  */
trait TProjectRoleSetBuilder extends RoleSetBuilder[ProjectUserRole] {

  override def newRole(userId: ObjectReference, role: Role) = new ProjectUserRole(userId, role, -1L, false, true)

}
