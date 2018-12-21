package form.project

import db.{DbRef, InsertFunc}
import form.RoleSetBuilder
import models.user.User
import models.user.role.ProjectUserRole
import ore.permission.role.Role

/**
  * Takes form data and builds an uninitialized set of [[ProjectUserRole]].
  */
trait TProjectRoleSetBuilder extends RoleSetBuilder[ProjectUserRole.Partial] {

  override def newRole(userId: DbRef[User], role: Role): ProjectUserRole.Partial =
    ProjectUserRole.Partial(userId, -1L, role)
}
