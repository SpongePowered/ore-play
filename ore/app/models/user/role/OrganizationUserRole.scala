package models.user.role

import db.impl.DefaultModelCompanion
import db.impl.schema.OrganizationRoleTable
import models.user.{Organization, User}
import ore.Visitable
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.organization.OrganizationOwned
import ore.permission.role.Role
import ore.user.UserOwned

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a [[UserRoleModel]] within an [[models.user.Organization]].
  *
  * @param userId         ID of User this role belongs to
  * @param organizationId ID of Organization this role belongs to
  * @param role      Type of Role
  * @param isAccepted    True if has been accepted
  */
case class OrganizationUserRole(
    userId: DbRef[User],
    organizationId: DbRef[Organization],
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel[OrganizationUserRole] {

  override def subject(implicit service: ModelService): IO[Model[Visitable]] =
    OrganizationOwned[OrganizationUserRole].organization(this)

  override def withRole(role: Role): OrganizationUserRole = copy(role = role)

  override def withAccepted(accepted: Boolean): OrganizationUserRole = copy(isAccepted = accepted)
}
object OrganizationUserRole
    extends DefaultModelCompanion[OrganizationUserRole, OrganizationRoleTable](TableQuery[OrganizationRoleTable]) {

  implicit val query: ModelQuery[OrganizationUserRole] = ModelQuery.from(this)

  implicit val isOrgOwned: OrganizationOwned[OrganizationUserRole] = (a: OrganizationUserRole) => a.organizationId
  implicit val isUserOwned: UserOwned[OrganizationUserRole]        = (a: OrganizationUserRole) => a.userId
}
