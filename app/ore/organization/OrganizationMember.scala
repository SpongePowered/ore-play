package ore.organization

import db.{DbRef, ModelService}
import models.user.role.OrganizationUserRole
import models.user.{Organization, User}
import ore.user.{Member, UserOwned}

import cats.effect.IO

/**
  * Represents a [[models.user.User]] member of an [[Organization]].
  *
  * @param organization Organization member belongs to
  * @param userId       User ID
  */
class OrganizationMember(val organization: Organization, val userId: DbRef[User]) extends Member[OrganizationUserRole] {

  override def roles(implicit service: ModelService): IO[Set[OrganizationUserRole]] =
    UserOwned[OrganizationMember].user(this).flatMap(user => this.organization.memberships.getRoles(organization, user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit service: ModelService): IO[OrganizationUserRole] =
    this.roles.map(role => role.maxBy(_.role.trust))

}
object OrganizationMember {
  implicit val isUserOwned: UserOwned[OrganizationMember] = (a: OrganizationMember) => a.userId
}
