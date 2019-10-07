package form.organization

import ore.db.DbRef
import ore.models.user.User
import ore.models.user.role.OrganizationUserRole

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
