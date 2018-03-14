package ore.user.notification

import db.impl.OrePostgresDriver
import db.table.MappedType
import slick.jdbc.JdbcType

import scala.language.implicitConversions

/**
  * Represents the different types of notifications.
  */
object NotificationTypes extends Enumeration {

  val ProjectInvite = NotificationType(0)
  val OrganizationInvite = NotificationType(1)
  val NewProjectVersion = NotificationType(2)
  val VersionReviewed = NotificationType(3)

  case class NotificationType(i: Int) extends super.Val(i) with MappedType[NotificationType] {
    implicit val mapper: JdbcType[NotificationType] = OrePostgresDriver.api.notificationTypeTypeMapper
  }
  implicit def convert(value: Value): NotificationType = value.asInstanceOf[NotificationType]

}
