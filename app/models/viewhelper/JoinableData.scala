package models.viewhelper

import controllers.sugar.Requests.OreRequest
import db.Model
import models.user.User
import models.user.role.UserRoleModel
import ore.Joinable
import ore.permission.EditSettings
import ore.permission.role.RoleCategory
import ore.user.Member

trait JoinableData[R <: UserRoleModel, M <: Member[R], T <: Joinable[M, T] with Model] {

  def joinable: T
  def members: Seq[(R, User)]

  def roleCategory: RoleCategory

  def filteredMembers(implicit request: OreRequest[_]): Seq[(R, User)] = {
    val hasEditSettings = request.headerData.globalPerm(EditSettings)
    val userIsOwner     = request.currentUser.map(_.id.value).contains(joinable.ownerId)
    if (hasEditSettings || userIsOwner)
      members
    else
      members.filter {
        case (role, _) => role.isAccepted // project role is accepted
      }
  }
}
