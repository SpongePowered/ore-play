package models.user

import java.sql.Timestamp

import db.Model
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
  * @param message          Message to display
  * @param action           Action to perform on click
  * @param read             True if notification has been read
  */
case class Notification(override val id: Option[Int] = None,
                        override val createdAt: Option[Timestamp] = None,
                        override val userId: Int = -1,
                        originId: Int,
                        notificationType: NotificationType,
                        message: String,
                        action: Option[String] = None,
                        private var read: Boolean = false)
                        extends OreModel(id, createdAt)
                          with UserOwned {

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
  def setRead(read: Boolean) = Defined {
    this.read = read
    update(Read)
  }

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): Model = this.copy(id = id, createdAt = theTime)

}
