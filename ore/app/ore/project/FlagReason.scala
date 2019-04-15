package ore.project

import scala.collection.immutable

import enumeratum.values._

/**
  * Represents the reasons for submitting a [[models.project.Flag]].
  */
sealed abstract class FlagReason(val value: Int, val title: String) extends IntEnumEntry
object FlagReason extends IntEnum[FlagReason] {

  val values: immutable.IndexedSeq[FlagReason] = findValues

  case object InappropriateContent extends FlagReason(0, "Inappropriate Content")
  case object Impersonation        extends FlagReason(1, "Impersonation or Deception")
  case object Spam                 extends FlagReason(2, "Spam")
  case object MalIntent            extends FlagReason(3, "Malicious Intent")
  case object Other                extends FlagReason(4, "Other")
}
