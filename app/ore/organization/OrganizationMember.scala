package ore.organization

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.{ModelService, ObjectReference}
import models.user.Organization
import models.user.role.OrganizationRole
import ore.user.Member

/**
  * Represents a [[models.user.User]] member of an [[Organization]].
  *
  * @param organization Organization member belongs to
  * @param userId       User ID
  * @param users        UserBase instance
  */
class OrganizationMember(val organization: Organization, override val userId: ObjectReference)(implicit users: UserBase)
    extends Member[OrganizationRole](userId) {

  override def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[OrganizationRole]] =
    this.user.flatMap(user => this.organization.memberships.getRoles(user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext, service: ModelService): Future[OrganizationRole] =
    this.roles.map(role => role.maxBy(_.roleType.trust))

}
