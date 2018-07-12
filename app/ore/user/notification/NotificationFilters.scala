package ore.user.notification

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import models.user.Notification

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * A collection of ways to filter notifications.
  */
object NotificationFilters extends Enumeration {

  val Unread = NotificationFilter(0, "unread", "notification.empty.unread", "notification.unread", !_.read)
  val Read = NotificationFilter(1, "read", "notification.empty.read", "notification.read", _.read)
  val All = NotificationFilter(2, "all", "notification.empty.all", "notification.all", _ => true)

  case class NotificationFilter(i: Int,
                                name: String,
                                emptyMessage: String,
                                title: String,
                                filter: Notification#T => Rep[Boolean]) extends super.Val(i, name) {

    def apply(notifications: ModelAccess[Notification])(implicit ec: ExecutionContext): Future[Seq[Notification]] = notifications.filter(this.filter)

  }

  implicit def convert(value: Value): NotificationFilter = value.asInstanceOf[NotificationFilter]

}
