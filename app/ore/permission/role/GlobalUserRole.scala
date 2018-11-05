package ore.permission.role

import db.DbRef
import models.user.User
import ore.permission.scope.GlobalScope

/**
  * Represents a [[UserRole]] within the [[GlobalScope]].
  *
  * @param userId   ID of [[models.user.User]] this role belongs to
  * @param role Type of role
  */
case class GlobalUserRole(override val userId: DbRef[User], override val role: Role) extends UserRole
