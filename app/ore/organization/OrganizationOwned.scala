package ore.organization

import scala.concurrent.{ExecutionContext, Future}

import db.ObjectReference
import db.impl.access.OrganizationBase
import models.user.Organization

import cats.instances.future._

/**
  * Represents anything that has an [[Organization]].
  */
trait OrganizationOwned {

  /** Returns the Organization's ID */
  def organizationId: ObjectReference

  /** Returns the Organization */
  def organization(implicit organizations: OrganizationBase, ec: ExecutionContext): Future[Organization] =
    organizations.get(this.organizationId).getOrElse(throw new NoSuchElementException("Get on None"))
}
