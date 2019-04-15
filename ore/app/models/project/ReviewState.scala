package models.project

import scala.collection.immutable

import enumeratum.values._

sealed abstract class ReviewState(val value: Int, val apiName: String) extends IntEnumEntry {

  def isChecked: Boolean = this == ReviewState.Reviewed || this == ReviewState.PartiallyReviewed
}
object ReviewState extends IntEnum[ReviewState] {
  case object Unreviewed        extends ReviewState(0, "unreviewed")
  case object Reviewed          extends ReviewState(1, "reviewed")
  case object Backlog           extends ReviewState(2, "backlog")
  case object PartiallyReviewed extends ReviewState(3, "partially_reviewed")

  val values: immutable.IndexedSeq[ReviewState] = findValues
}
