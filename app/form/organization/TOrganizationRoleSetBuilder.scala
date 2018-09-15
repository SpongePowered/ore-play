package form.organization

import form.RoleSetBuilder
import models.user.role.OrganizationRole
import ore.permission.role.RoleType

/**
  * Builds a set of [[OrganizationRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationRole] {

  override def newRole(userId: Int, role: RoleType): OrganizationRole = new OrganizationRole(userId, role)

}
