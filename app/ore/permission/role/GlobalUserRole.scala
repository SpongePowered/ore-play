package ore.permission.role

import db.ObjectReference
import ore.permission.scope.GlobalScope
import ore.user.UserOwned

/**
  * Represents a [[UserRole]] within the [[GlobalScope]].
  *
  * @param userId   ID of [[models.user.User]] this role belongs to
  * @param role Type of role
  */
case class GlobalUserRole(userId: ObjectReference, override val role: Role) extends UserRole
object GlobalUserRole {
  implicit val isUserOwned: UserOwned[GlobalUserRole] = (a: GlobalUserRole) => a.userId
}
