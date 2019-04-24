package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.user.{Notification, User}
import ore.db.DbRef
import ore.user.notification.NotificationType

import cats.data.NonEmptyList

class NotificationTable(tag: Tag) extends ModelTable[Notification](tag, "notifications") {

  def userId           = column[DbRef[User]]("user_id")
  def originId         = column[Option[DbRef[User]]]("origin_id")
  def notificationType = column[NotificationType]("notification_type")
  def messageArgs      = column[NonEmptyList[String]]("message_args")
  def action           = column[String]("action")
  def read             = column[Boolean]("read")

  override def * =
    (id.?, createdAt.?, (userId, originId, notificationType, messageArgs, action.?, read)) <> (mkApply(
      (Notification.apply _).tupled
    ), mkUnapply(Notification.unapply))
}
