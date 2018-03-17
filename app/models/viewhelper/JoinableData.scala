package models.viewhelper

import models.user.User
import models.user.role.RoleModel
import ore.Joinable
import ore.permission.role.Role
import ore.permission.{EditSettings, Permission}
import ore.user.Member

trait JoinableData[R <: RoleModel, M <: Member[R], T <: Joinable[M]] {

  val headerData: HeaderData

  val permissions: Map[Permission, Boolean]
  val joinable: T
  val ownerRole: R
  val members: Seq[(R, User)]

  def roleClass = ownerRole.getClass.asInstanceOf[Class[_ <: Role]]

  def apply(permission: Permission): Boolean = permissions.getOrElse(permission, false)

  def filteredMembers = {
    if (this.apply(EditSettings) || // has EditSettings show all
      headerData.currentUser.map(_.id.get).contains(joinable.ownerId) // Current User is owner
    ) members
    else {
      members.filter {
        case (role, _) => role.isAccepted // project role is accepted
      }
    }
  }
}
