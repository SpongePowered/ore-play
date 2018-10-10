package form.organization

import db.ObjectReference
import form.RoleSetBuilder
import models.user.role.OrganizationRole
import ore.permission.role.RoleType

/**
  * Builds a set of [[OrganizationRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationRole] {

  override def newRole(userId: ObjectReference, role: RoleType): OrganizationRole =
    new OrganizationRole(userId, -1L, role) //orgId set elsewhere

}
