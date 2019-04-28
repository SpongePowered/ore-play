package ore.models.user.role

import scala.language.higherKinds

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.Visitable
import ore.db.impl.schema.OrganizationRoleTable
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.organization.{Organization, OrganizationOwned}
import ore.models.user.{User, UserOwned}
import ore.permission.role.Role

import cats.MonadError
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Represents a [[UserRoleModel]] within an [[Organization]].
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

  override def subject[F[_]: ModelService](implicit F: MonadError[F, Throwable]): F[Model[Visitable]] =
    OrganizationOwned[OrganizationUserRole].organization(this).widen[Model[Visitable]]

  override def withRole(role: Role): OrganizationUserRole = copy(role = role)

  override def withAccepted(accepted: Boolean): OrganizationUserRole = copy(isAccepted = accepted)
}
object OrganizationUserRole
    extends DefaultModelCompanion[OrganizationUserRole, OrganizationRoleTable](TableQuery[OrganizationRoleTable]) {

  implicit val query: ModelQuery[OrganizationUserRole] = ModelQuery.from(this)

  implicit val isOrgOwned: OrganizationOwned[OrganizationUserRole] = (a: OrganizationUserRole) => a.organizationId
  implicit val isUserOwned: UserOwned[OrganizationUserRole]        = (a: OrganizationUserRole) => a.userId
}
