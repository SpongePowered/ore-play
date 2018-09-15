package models.user

import java.sql.Timestamp

import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.NotificationTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import ore.user.UserOwned
import ore.user.notification.NotificationTypes.NotificationType
import util.instances.future._

import scala.concurrent.{ExecutionContext, Future}

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
  * @param read             True if notification has been read
  */
case class Notification(override val id: ObjectId = ObjectId.Uninitialized,
                        override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                        override val userId: ObjectReference = -1,
                        originId: ObjectReference,
                        notificationType: NotificationType,
                        messageArgs: List[String],
                        action: Option[String] = None,
                        private var read: Boolean = false)
                        extends OreModel(id, createdAt)
                          with UserOwned {
  //TODO: Would be neat to have a NonEmptyList to get around guarding against this
  require(messageArgs.nonEmpty, "Notification created with no message arguments")

  override type M = Notification
  override type T = NotificationTable

  /**
    * Returns the [[User]] from which this Notification originated from.
    *
    * @return User from which this originated from
    */
  def origin(implicit ec: ExecutionContext): Future[User] =
    this.userBase.get(this.originId).getOrElse(throw new NoSuchElementException("Get on None"))

  /**
    * Returns true if this notification has been read.
    *
    * @return True if read
    */
  def isRead: Boolean = this.read

  /**
    * Sets this notification as read or unread.
    *
    * @param read True if has been read
    */
  def setRead(read: Boolean): Future[Int] = Defined {
    this.read = read
    update(Read)
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = theTime)

}
