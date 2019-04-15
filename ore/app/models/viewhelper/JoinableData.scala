package models.viewhelper

import controllers.sugar.Requests.OreRequest
import db.Model
import models.user.User
import models.user.role.UserRoleModel
import ore.Joinable
import ore.permission.Permission
import ore.permission.role.RoleCategory

trait JoinableData[R <: UserRoleModel[R], T <: Joinable] {

  def joinable: Model[T]
  def members: Seq[(Model[R], Model[User])]

  def roleCategory: RoleCategory

  def filteredMembers(implicit request: OreRequest[_]): Seq[(Model[R], Model[User])] = {
    val hasEditMembers = request.headerData.globalPerm(Permission.ManageSubjectMembers)
    val userIsOwner    = request.currentUser.map(_.id.value).contains(joinable.ownerId)
    if (hasEditMembers || userIsOwner)
      members
    else
      members.filter {
        case (role, _) => role.isAccepted // project role is accepted
      }
  }
}
