package form.organization

import models.user.User
import models.user.role.OrganizationUserRole
import ore.db.DbRef

/**
  * Builds a set of [[OrganizationUserRole]]s from input data.
  *
  * @param users User IDs
  * @param roles Role names
  */
case class OrganizationRoleSetBuilder(
    name: String,
    users: List[DbRef[User]],
    roles: List[String]
) extends TOrganizationRoleSetBuilder
