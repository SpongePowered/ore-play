package form.organization

import db.ObjectReference
import form.RoleSetBuilder
import models.user.role.OrganizationUserRole
import ore.permission.role.Role

/**
  * Builds a set of [[OrganizationUserRole]]s from input data.
  */
trait TOrganizationRoleSetBuilder extends RoleSetBuilder[OrganizationUserRole] {

  override def newRole(userId: ObjectReference, role: Role): OrganizationUserRole =
    new OrganizationUserRole(userId, -1L, role) //orgId set elsewhere

}
