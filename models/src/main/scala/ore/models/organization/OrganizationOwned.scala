package ore.models.organization

import scala.language.{higherKinds, implicitConversions}

import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

import cats.MonadError
import simulacrum.typeclass

/**
  * Represents anything that has an [[Organization]].
  */
@typeclass trait OrganizationOwned[A] {

  /** Returns the Organization's ID */
  def organizationId(a: A): DbRef[Organization]

  /** Returns the Organization */
  def organization[F[_]](a: A)(implicit service: ModelService[F], F: MonadError[F, Throwable]): F[Model[Organization]] =
    ModelView
      .now(Organization)
      .get(organizationId(a))
      .getOrElseF(F.raiseError(new NoSuchElementException("Get on None")))
}
object OrganizationOwned {

  implicit def isOrgOwned[A](implicit isOwned: OrganizationOwned[A]): OrganizationOwned[Model[A]] =
    (a: Model[A]) => isOwned.organizationId(a.obj)
}
