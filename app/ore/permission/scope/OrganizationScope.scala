package ore.permission.scope

import ore.organization.OrganizationOwned

/**
  * Represents a scope of a certain [[models.user.Organization]].
  */
trait OrganizationScope extends Scope with OrganizationOwned
