package ore.organization

import db.impl.access.OrganizationBase
import models.user.Organization

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents anything that has an [[Organization]].
  */
trait OrganizationOwned {
  /** Returns the Organization's ID */
  def organizationId: Int
  /** Returns the Organization */
  def organization(implicit organizations: OrganizationBase, ec: ExecutionContext): Future[Organization] = organizations.get(this.organizationId).map(_.get)
}
