package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.OrganizationRoleTable
import db.{Model, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.Visitable
import ore.organization.OrganizationOwned
import ore.permission.role.Role
import ore.user.UserOwned

/**
  * Represents a [[UserRoleModel]] within an [[models.user.Organization]].
  *
  * @param id             Model ID
  * @param createdAt      Timestamp instant of creation
  * @param userId         ID of User this role belongs to
  * @param organizationId ID of Organization this role belongs to
  * @param role      Type of Role
  * @param isAccepted    True if has been accepted
  */
case class OrganizationUserRole(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    organizationId: ObjectReference,
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel {

  override type M = OrganizationUserRole
  override type T = OrganizationRoleTable

  def this(userId: ObjectReference, organizationId: ObjectReference, roleType: Role) =
    this(id = ObjectId.Uninitialized, userId = userId, organizationId = organizationId, role = roleType)

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] =
    OrganizationOwned[OrganizationUserRole].organization(this)
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = theTime)
}
object OrganizationUserRole {
  implicit val isOrgOwned: OrganizationOwned[OrganizationUserRole] = (a: OrganizationUserRole) => a.organizationId
  implicit val isUserOwned: UserOwned[OrganizationUserRole]        = (a: OrganizationUserRole) => a.userId
}
