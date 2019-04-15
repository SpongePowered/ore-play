package db.impl.schema

import db.DbRef
import db.table.AssociativeTable
import db.impl.OrePostgresDriver.api._
import models.user.User
import models.user.role.DbRole

class UserGlobalRolesTable(tag: Tag) extends AssociativeTable[User, DbRole](tag, "user_global_roles") {

  def userId = column[DbRef[User]]("user_id")
  def roleId = column[DbRef[DbRole]]("role_id")

  override def * = (userId, roleId)
}
