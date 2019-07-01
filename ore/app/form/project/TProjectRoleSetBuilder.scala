package form.project

import form.RoleSetBuilder
import ore.db.DbRef
import ore.models.user.User
import ore.models.user.role.ProjectUserRole
import ore.permission.role.Role

/**
  * Takes form data and builds an uninitialized set of [[ProjectUserRole]].
  */
trait TProjectRoleSetBuilder extends RoleSetBuilder[ProjectUserRole] {

  override def newRole(userId: DbRef[User], role: Role): ProjectUserRole =
    ProjectUserRole(userId, -1L, role)
}
