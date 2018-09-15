package models.viewhelper

import controllers.sugar.Requests.OreRequest
import models.user.User
import models.user.role.RoleModel
import ore.Joinable
import ore.permission.EditSettings
import ore.permission.role.Role
import ore.user.Member

trait JoinableData[R <: RoleModel, M <: Member[R], T <: Joinable[M]] {

  val joinable: T
  val ownerRole: R
  val members: Seq[(R, User)]

  def roleClass: Class[_ <: Role] = ownerRole.getClass.asInstanceOf[Class[_ <: Role]]

  def filteredMembers(implicit request: OreRequest[_]): Seq[(R, User)] = {
    if (request.data.globalPerm(EditSettings) || // has EditSettings show all
      request.currentUser.map(_.id.value).contains(joinable.ownerId) // Current User is owner
    ) members
    else {
      members.filter {
        case (role, _) => role.isAccepted // project role is accepted
      }
    }
  }
}
