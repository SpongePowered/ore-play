package ore.organization

import db.impl.access.UserBase
import models.user.Organization
import models.user.role.{OrganizationRole, ProjectRole}
import ore.permission.scope.Scope
import ore.user.Member

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a [[models.user.User]] member of an [[Organization]].
  *
  * @param organization Organization member belongs to
  * @param userId       User ID
  * @param users        UserBase instance
  */
class OrganizationMember(val organization: Organization, override val userId: Int)(implicit users: UserBase)
  extends Member[OrganizationRole](userId) {

  override def roles(implicit ec: ExecutionContext): Future[Set[OrganizationRole]] = this.user.flatMap(user => this.organization.memberships.getRoles(user))

  override def scope: Scope = this.organization.scope

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext): Future[OrganizationRole] =
    this.roles.map(role => role.max(ordering))

  private def ordering: Ordering[OrganizationRole]  =
    Ordering.by(r => r.roleType.trust)

}

