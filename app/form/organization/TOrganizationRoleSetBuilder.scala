package form.organization

import db.{DbRef, InsertFunc}
import form.RoleSetBuilder
import models.user.User
import models.user.role.OrganizationUserRole
import ore.permission.role.Role

/**
  * Builds a set of [[OrganizationUserRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationUserRole.Partial] {

  override def newRole(userId: DbRef[User], role: Role): OrganizationUserRole.Partial =
    OrganizationUserRole.Partial(userId, -1L, role) //orgId set elsewhere
}
