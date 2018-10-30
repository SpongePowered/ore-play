package ore.permission.role

import db.DbRef
import models.user.User
import ore.permission.scope.GlobalScope

/**
  * Represents a [[Role]] within the [[GlobalScope]].
  *
  * @param userId   ID of [[models.user.User]] this role belongs to
  * @param roleType Type of role
  */
case class GlobalRole(override val userId: DbRef[User], override val roleType: RoleType) extends Role
