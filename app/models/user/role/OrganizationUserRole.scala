package models.user.role

import db.impl.schema.OrganizationRoleTable
import db.{DbRef, InsertFunc, ModelQuery, ModelService, ObjId, ObjTimestamp}
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
case class OrganizationUserRole private (
    id: ObjId[OrganizationUserRole],
    createdAt: ObjTimestamp,
    userId: DbRef[User],
    organizationId: DbRef[Organization],
    role: Role,
    isAccepted: Boolean
) extends UserRoleModel {

  override type M = OrganizationUserRole
  override type T = OrganizationRoleTable

  override def subject(implicit service: ModelService): IO[Visitable] =
    OrganizationOwned[OrganizationUserRole].organization(this)
}
object OrganizationUserRole {
  case class Partial(
      userId: DbRef[User],
      organizationId: DbRef[Organization],
      role: Role,
      isAccepted: Boolean = false
  ) {

    def asFunc: InsertFunc[OrganizationUserRole] =
      (id, time) => OrganizationUserRole(id, time, userId, organizationId, role, isAccepted)
  }

  implicit val query: ModelQuery[OrganizationUserRole] =
    ModelQuery.from[OrganizationUserRole](TableQuery[OrganizationRoleTable], _.copy(_, _))

  implicit val isOrgOwned: OrganizationOwned[OrganizationUserRole] = (a: OrganizationUserRole) => a.organizationId
  implicit val isUserOwned: UserOwned[OrganizationUserRole]        = (a: OrganizationUserRole) => a.userId
}
