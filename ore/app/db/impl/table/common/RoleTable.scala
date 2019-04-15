package db.impl.table.common

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.user.User
import models.user.role.UserRoleModel
import ore.permission.role.Role

trait RoleTable[R <: UserRoleModel[R]] extends ModelTable[R] {

  def userId     = column[DbRef[User]]("user_id")
  def roleType   = column[Role]("role_type")
  def isAccepted = column[Boolean]("is_accepted")
}
