package ore.permission.role

import ore.permission.scope.ScopeSubject
import ore.user.UserOwned

/**
  * Represents a "role" that is posessed by a [[models.user.User]].
  */
trait UserRole extends ScopeSubject with UserOwned {

  /** The role itself */
  def role: Role
}
