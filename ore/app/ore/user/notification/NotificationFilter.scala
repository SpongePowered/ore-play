package ore.user.notification

import scala.language.higherKinds

import scala.collection.immutable

import db.Model
import db.access.QueryView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.NotificationTable
import models.user.Notification
import util.syntax._

import enumeratum.values._

sealed abstract class NotificationFilter(
    val value: Int,
    val name: String,
    val emptyMessage: String,
    val title: String,
    val filter: NotificationTable => Rep[Boolean]
) extends IntEnumEntry {

  def apply[V[_, _]: QueryView](
      notifications: V[NotificationTable, Model[Notification]]
  ): V[NotificationTable, Model[Notification]] =
    notifications.filterView(this.filter)
}

/**
  * A collection of ways to filter notifications.
  */
object NotificationFilter extends IntEnum[NotificationFilter] {

  val values: immutable.IndexedSeq[NotificationFilter] = findValues

  case object Unread
      extends NotificationFilter(0, "unread", "notification.empty.unread", "notification.unread", !_.read)
  case object Read extends NotificationFilter(1, "read", "notification.empty.read", "notification.read", _.read)
  case object All  extends NotificationFilter(2, "all", "notification.empty.all", "notification.all", _ => true)
}
