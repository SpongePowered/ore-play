package form.organization

import form.RoleSetBuilder
import ore.models.user.User
import ore.models.user.role.OrganizationUserRole
import ore.db.DbRef
import ore.permission.role.Role

/**
  * Builds a set of [[OrganizationUserRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationUserRole] {

  override def newRole(userId: DbRef[User], role: Role): OrganizationUserRole =
    OrganizationUserRole(userId, -1L, role) //orgId set elsewhere
}
