package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.user.User
import models.user.role.DbRole
import ore.db.DbRef

class UserGlobalRolesTable(tag: Tag) extends AssociativeTable[User, DbRole](tag, "user_global_roles") {

  def userId = column[DbRef[User]]("user_id")
  def roleId = column[DbRef[DbRole]]("role_id")

  override def * = (userId, roleId)
}
