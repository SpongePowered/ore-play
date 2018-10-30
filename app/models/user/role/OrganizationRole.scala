package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.OrganizationRoleTable
import db.{DbRef, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.user.{Organization, User}
import ore.Visitable
import ore.organization.OrganizationOwned
import ore.permission.role.RoleType

import slick.lifted.TableQuery

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
    id: ObjId[OrganizationRole] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: DbRef[User],
    organizationId: DbRef[Organization],
    roleType: RoleType,
    isAccepted: Boolean = false
) extends RoleModel
    with OrganizationOwned {

  override type M = OrganizationRole
  override type T = OrganizationRoleTable

  def this(userId: DbRef[User], organizationId: DbRef[Organization], roleType: RoleType) =
    this(id = ObjId.Uninitialized(), userId = userId, organizationId = organizationId, roleType = roleType)

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] = this.organization
}
object OrganizationRole {
  implicit val query: ModelQuery[OrganizationRole] =
    ModelQuery.from[OrganizationRole](TableQuery[OrganizationRoleTable], _.copy(_, _))
}
