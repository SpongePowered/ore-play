package ore.db.impl.table.common

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.User
import ore.models.user.role.UserRoleModel
import ore.permission.role.Role

trait RoleTable[R <: UserRoleModel[R]] extends ModelTable[R] {

  def userId     = column[DbRef[User]]("user_id")
  def roleType   = column[Role]("role_type")
  def isAccepted = column[Boolean]("is_accepted")
}
