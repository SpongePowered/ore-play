package models.user.role

import db.impl.schema.OrganizationRoleTable
import db.{DbRef, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.user.{Organization, User}
import ore.Visitable
import ore.organization.OrganizationOwned
import ore.permission.role.Role
import ore.user.UserOwned

import cats.effect.IO
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
    id: ObjId[OrganizationUserRole] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: DbRef[User],
    organizationId: DbRef[Organization],
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel {

  override type M = OrganizationUserRole
  override type T = OrganizationRoleTable

  def this(userId: DbRef[User], organizationId: DbRef[Organization], roleType: Role) =
    this(id = ObjId.Uninitialized(), userId = userId, organizationId = organizationId, role = roleType)

  override def subject(implicit service: ModelService): IO[Visitable] =
    OrganizationOwned[OrganizationUserRole].organization(this)
}
object OrganizationUserRole {
  implicit val query: ModelQuery[OrganizationUserRole] =
    ModelQuery.from[OrganizationUserRole](TableQuery[OrganizationRoleTable], _.copy(_, _))

  implicit val isOrgOwned: OrganizationOwned[OrganizationUserRole] = (a: OrganizationUserRole) => a.organizationId
  implicit val isUserOwned: UserOwned[OrganizationUserRole]        = (a: OrganizationUserRole) => a.userId
}
