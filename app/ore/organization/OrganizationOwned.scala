package ore.organization

import scala.language.implicitConversions

import db.access.ModelView
import db.{Model, DbRef, ModelService}
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
  def organization(a: A)(implicit service: ModelService): IO[Model[Organization]] =
    ModelView
      .now(Organization)
      .get(organizationId(a))
      .getOrElseF(IO.raiseError(new NoSuchElementException("Get on None")))
}
