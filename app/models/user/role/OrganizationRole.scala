package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.OrganizationRoleTable
import db.{Model, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.Visitable
import ore.permission.role.RoleType
import ore.permission.scope.OrganizationScope

/**
  * Represents a [[RoleModel]] within an [[models.user.Organization]].
  *
  * @param id             Model ID
  * @param createdAt      Timestamp instant of creation
  * @param userId         ID of User this role belongs to
  * @param organizationId ID of Organization this role belongs to
  * @param roleType      Type of Role
  * @param isAccepted    True if has been accepted
  */
case class OrganizationRole(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    organizationId: ObjectReference,
    roleType: RoleType,
    isAccepted: Boolean = false
) extends RoleModel
    with OrganizationScope {

  override type M = OrganizationRole
  override type T = OrganizationRoleTable

  def this(userId: ObjectReference, roleType: RoleType) =
    this(id = ObjectId.Uninitialized, userId = userId, roleType = roleType)

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] = this.organization
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model                          = this.copy(id = id, createdAt = theTime)
}
