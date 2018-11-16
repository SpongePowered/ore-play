package ore.permission.role

/**
  * Represents a "role" that is posessed by a [[models.user.User]].
  */
trait UserRole {

  /** The role itself */
  def role: Role
}
