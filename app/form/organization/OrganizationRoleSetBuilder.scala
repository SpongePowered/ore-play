package form.organization

import db.ObjectReference
import models.user.role.OrganizationUserRole

/**
  * Builds a set of [[OrganizationUserRole]]s from input data.
  *
  * @param users User IDs
  * @param roles Role names
  */
case class OrganizationRoleSetBuilder(
    name: String,
    users: List[ObjectReference],
    roles: List[String]
) extends TOrganizationRoleSetBuilder
