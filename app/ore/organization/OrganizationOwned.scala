package ore.organization

import scala.language.implicitConversions

import scala.concurrent.{ExecutionContext, Future}

import db.ObjectReference
import db.impl.access.OrganizationBase
import models.user.Organization

import cats.instances.future._
import simulacrum.typeclass

/**
  * Represents anything that has an [[Organization]].
  */
@typeclass trait OrganizationOwned[A] {

  /** Returns the Organization's ID */
  def organizationId(a: A): ObjectReference

  /** Returns the Organization */
  def organization(a: A)(implicit organizations: OrganizationBase, ec: ExecutionContext): Future[Organization] =
    organizations.get(organizationId(a)).getOrElse(throw new NoSuchElementException("Get on None"))
}
