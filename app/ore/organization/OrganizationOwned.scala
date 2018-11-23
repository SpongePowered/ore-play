package ore.organization

import scala.language.implicitConversions

import db.DbRef
import db.impl.access.OrganizationBase
import models.user.Organization

import cats.effect.IO
import simulacrum.typeclass

/**
  * Represents anything that has an [[Organization]].
  */
@typeclass trait OrganizationOwned[A] {

  /** Returns the Organization's ID */
  def organizationId(a: A): DbRef[Organization]

  /** Returns the Organization */
  def organization(a: A)(implicit organizations: OrganizationBase): IO[Organization] =
    organizations.get(organizationId(a)).getOrElse(throw new NoSuchElementException("Get on None"))
}
