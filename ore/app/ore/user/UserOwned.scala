package ore.user

import scala.language.implicitConversions

import db.access.ModelView
import db.{Model, DbRef, ModelService}
import models.user.User

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
