package ore.user

import db.impl.access.UserBase
import models.user.User

import scala.concurrent.{ExecutionContext, Future}

/** Represents anything that has a [[User]]. */
trait UserOwned {
  /** Returns the User ID */
  def userId: Int
  /** Returns the User */
  def user(implicit users: UserBase, ec: ExecutionContext): Future[User] = users.get(this.userId).map(_.get)
}
