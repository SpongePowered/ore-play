package ore.user

import scala.language.implicitConversions

import scala.concurrent.{ExecutionContext, Future}

import db.ObjectReference
import db.impl.access.UserBase
import models.user.User

import cats.instances.future._
import simulacrum.typeclass

/** Represents anything that has a [[User]]. */
@typeclass trait UserOwned[A] {

  /** Returns the User ID */
  def userId(a: A): ObjectReference

  /** Returns the User */
  def user(a: A)(implicit users: UserBase, ec: ExecutionContext): Future[User] =
    users.get(userId(a)).getOrElse(throw new NoSuchElementException("None on get"))
}
