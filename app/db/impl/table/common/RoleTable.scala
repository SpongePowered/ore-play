package db.impl.table.common

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.user.User
import models.user.role.RoleModel
import ore.permission.role.RoleType

trait RoleTable[R <: RoleModel] extends ModelTable[R] {

  def userId     = column[DbRef[User]]("user_id")
  def roleType   = column[RoleType]("role_type")
  def isAccepted = column[Boolean]("is_accepted")
}
