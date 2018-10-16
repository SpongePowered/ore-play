package ore.permission.role

import ore.user.UserOwned

/**
  * Represents a "role" that is posessed by a [[models.user.User]].
  */
trait Role extends UserOwned {

  /** Type of role */
  def roleType: RoleType
}
