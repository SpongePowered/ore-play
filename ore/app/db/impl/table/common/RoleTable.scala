package db.impl.table.common

import db.impl.OrePostgresDriver.api._
import models.user.User
import models.user.role.UserRoleModel
import ore.db.DbRef
import ore.permission.role.Role

trait RoleTable[R <: UserRoleModel[R]] extends ModelTable[R] {

  def userId     = column[DbRef[User]]("user_id")
  def roleType   = column[Role]("role_type")
  def isAccepted = column[Boolean]("is_accepted")
}
