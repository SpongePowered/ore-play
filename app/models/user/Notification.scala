package models.user

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.impl.schema.NotificationTable
import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import ore.user.UserOwned
import ore.user.notification.NotificationType

import cats.data.{NonEmptyList => NEL}
import cats.instances.future._

/**
  * Represents a [[User]] notification.
  *
  * @param id               Unique ID
  * @param createdAt        Instant of cretion
  * @param userId           ID of User this notification belongs to
  * @param notificationType Type of notification
  * @param messageArgs      The unlocalized message to display, with the
  *                         parameters to use when localizing
  * @param action           Action to perform on click
  * @param isRead             True if notification has been read
  */
case class Notification(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    originId: ObjectReference,
    notificationType: NotificationType,
    messageArgs: NEL[String],
    action: Option[String] = None,
    isRead: Boolean = false
) extends Model {

  override type M = Notification
  override type T = NotificationTable

  /**
    * Returns the [[User]] from which this Notification originated from.
    *
    * @return User from which this originated from
    */
  def origin(implicit ec: ExecutionContext, userBase: UserBase): Future[User] =
    userBase.get(this.originId).getOrElse(throw new NoSuchElementException("Get on None"))

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = theTime)
}
object Notification {
  implicit val isUserOwned: UserOwned[Notification] = (a: Notification) => a.userId
}
