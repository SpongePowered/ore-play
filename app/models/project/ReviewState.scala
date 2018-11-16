package models.project

import scala.collection.immutable

import enumeratum.values._

sealed abstract class ReviewState(val value: Int) extends IntEnumEntry {

  def isChecked: Boolean = this == ReviewState.Reviewed || this == ReviewState.PartiallyReviewed
}
object ReviewState extends IntEnum[ReviewState] {
  case object Unreviewed        extends ReviewState(0)
  case object Reviewed          extends ReviewState(1)
  case object Backlog           extends ReviewState(2)
  case object PartiallyReviewed extends ReviewState(3)

  val values: immutable.IndexedSeq[ReviewState] = findValues
}
