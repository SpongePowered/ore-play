package ore.user

import scala.language.implicitConversions

import db.DbRef
import db.impl.access.UserBase
import models.user.User

import cats.effect.IO
import simulacrum.typeclass

/** Represents anything that has a [[User]]. */
@typeclass trait UserOwned[A] {

  /** Returns the User ID */
  def userId(a: A): DbRef[User]

  /** Returns the User */
  def user(a: A)(implicit users: UserBase): IO[User] =
    users.get(userId(a)).getOrElse(throw new NoSuchElementException("None on get"))
}
