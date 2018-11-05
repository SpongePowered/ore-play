package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.OrganizationRoleTable
import db.{Model, ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.Visitable
import ore.organization.OrganizationOwned
import ore.permission.role.Role

import slick.lifted.TableQuery

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
) extends UserRoleModel
    with OrganizationOwned {

  override type M = OrganizationUserRole
  override type T = OrganizationRoleTable

  def this(userId: ObjectReference, organizationId: ObjectReference, roleType: Role) =
    this(id = ObjectId.Uninitialized, userId = userId, organizationId = organizationId, role = roleType)

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] = this.organization
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model                          = this.copy(id = id, createdAt = theTime)
}
object OrganizationUserRole {
  implicit val query: ModelQuery[OrganizationUserRole] =
    ModelQuery.from[OrganizationUserRole](TableQuery[OrganizationRoleTable])
}
