package ore.organization

import scala.language.implicitConversions

import models.user.Organization
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

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
object OrganizationOwned {

  implicit def isOrgOwned[A](implicit isOwned: OrganizationOwned[A]): OrganizationOwned[Model[A]] =
    (a: Model[A]) => isOwned.organizationId(a.obj)
}
