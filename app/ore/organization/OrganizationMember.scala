package ore.organization

import scala.concurrent.{ExecutionContext, Future}

import db.{DbRef, ModelService}
import models.user.{Organization, User}
import models.user.role.OrganizationUserRole
import ore.user.Member

/**
  * Represents a [[models.user.User]] member of an [[Organization]].
  *
  * @param organization Organization member belongs to
  * @param userId       User ID
  */
class OrganizationMember(val organization: Organization, val userId: DbRef[User]) extends Member[OrganizationUserRole] {

  override def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[OrganizationUserRole]] =
    this.user.flatMap(user => this.organization.memberships.getRoles(organization, user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext, service: ModelService): Future[OrganizationUserRole] =
    this.roles.map(role => role.maxBy(_.role.trust))

}
