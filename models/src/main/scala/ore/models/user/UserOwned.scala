package ore.models.user

import scala.language.{higherKinds, implicitConversions}

import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

import cats.MonadError
import simulacrum.typeclass

/** Represents anything that has a [[User]]. */
@typeclass trait UserOwned[A] {

  /** Returns the User ID */
  def userId(a: A): DbRef[User]

  /** Returns the User */
  def user[F[_]: ModelService](a: A)(implicit F: MonadError[F, Throwable]): F[Model[User]] =
    ModelView.now(User).get(userId(a)).getOrElseF(F.raiseError(new NoSuchElementException("None on get")))
}
object UserOwned {

  implicit def isUserOwned[A](implicit isOwned: UserOwned[A]): UserOwned[Model[A]] =
    (a: Model[A]) => isOwned.userId(a.obj)
}
