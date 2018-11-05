package ore.permission.role

import ore.user.UserOwned

/**
  * Represents a "role" that is posessed by a [[models.user.User]].
  */
trait UserRole extends UserOwned {

  /** The role itself */
  def role: Role
}
