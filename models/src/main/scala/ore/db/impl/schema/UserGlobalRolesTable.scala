package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.User
import ore.models.user.role.DbRole

class UserGlobalRolesTable(tag: Tag) extends AssociativeTable[User, DbRole](tag, "user_global_roles") {

  def userId = column[DbRef[User]]("user_id")
  def roleId = column[DbRef[DbRole]]("role_id")

  override def * = (userId, roleId)
}
