package ore.permission.role

import db.ObjectReference
import ore.permission.scope.GlobalScope

/**
  * Represents a [[UserRole]] within the [[GlobalScope]].
  *
  * @param userId   ID of [[models.user.User]] this role belongs to
  * @param role Type of role
  */
case class GlobalUserRole(override val userId: ObjectReference, override val role: Role) extends UserRole
