package form.organization

import db.DbRef
import form.RoleSetBuilder
import models.user.User
import models.user.role.OrganizationRole
import ore.permission.role.RoleType

/**
  * Builds a set of [[OrganizationRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationRole] {

  override def newRole(userId: DbRef[User], role: RoleType): OrganizationRole =
    new OrganizationRole(userId, -1L, role) //orgId set elsewhere

}
