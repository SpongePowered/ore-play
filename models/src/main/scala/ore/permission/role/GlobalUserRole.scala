package ore.permission.role

import ore.models.user.User
import ore.db.DbRef
import ore.permission.scope.GlobalScope
import ore.models.user.UserOwned

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
