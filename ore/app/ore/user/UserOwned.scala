package ore.user

import scala.language.implicitConversions

import models.user.User
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

import cats.effect.IO
import simulacrum.typeclass

/** Represents anything that has a [[User]]. */
@typeclass trait UserOwned[A] {

  /** Returns the User ID */
  def userId(a: A): DbRef[User]

  /** Returns the User */
  def user(a: A)(implicit service: ModelService): IO[Model[User]] =
    ModelView.now(User).get(userId(a)).getOrElseF(IO.raiseError(new NoSuchElementException("None on get")))
}
object UserOwned {

  implicit def isUserOwned[A](implicit isOwned: UserOwned[A]): UserOwned[Model[A]] =
    (a: Model[A]) => isOwned.userId(a.obj)
}
