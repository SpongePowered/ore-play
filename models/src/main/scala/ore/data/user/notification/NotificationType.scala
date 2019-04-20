package ore.data.user.notification

import scala.collection.immutable

import enumeratum.values._

/**
  * Represents the different types of notifications.
  */
sealed abstract class NotificationType(val value: Int) extends IntEnumEntry
object NotificationType extends IntEnum[NotificationType] {

  val values: immutable.IndexedSeq[NotificationType] = findValues

  case object ProjectInvite      extends NotificationType(0)
  case object OrganizationInvite extends NotificationType(1)
  case object NewProjectVersion  extends NotificationType(2)
  case object VersionReviewed    extends NotificationType(3)
}
