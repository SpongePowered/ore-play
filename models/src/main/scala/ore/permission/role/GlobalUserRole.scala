package ore.permission.role

import ore.db.DbRef
import ore.models.user.{User, UserOwned}
import ore.permission.scope.GlobalScope

/**
  * Represents a user's [[Role]] within the [[GlobalScope]].
  *
  * @param userId ID of [[ore.models.user.User]] this role belongs to
  * @param role   Type of role
  */
case class GlobalUserRole(userId: DbRef[User], role: Role)
object GlobalUserRole {
  implicit val isUserOwned: UserOwned[GlobalUserRole] = (a: GlobalUserRole) => a.userId
}
