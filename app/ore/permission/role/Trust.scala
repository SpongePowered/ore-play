package ore.permission.role

import scala.collection.immutable

import cats.Order
import enumeratum.values._

/**
  * Represents a level of trust within the application.
  */
sealed abstract class Trust(val value: Int) extends IntEnumEntry {
  def level: Int = value
}
object Trust extends IntEnum[Trust] {
  implicit val order: Order[Trust]       = (x: Trust, y: Trust) => x.level - y.level
  implicit val ordering: Ordering[Trust] = order.toOrdering

  /**
    * User has the default level of trust granted by signing up.
    */
  case object Default extends Trust(0)

  /**
    * User has a limited amount of trust and may perform certain actions not
    * afforded to regular [[models.user.User]]s.
    */
  case object Limited extends Trust(1)

  /**
    * User has a standard amount of trust and may perform moderator-like actions
    * within the site.
    */
  case object Moderation extends Trust(2)

  /**
    * Users who can publish versions
    */
  case object Publish extends Trust(3)

  /**
    * User that can perform almost any action but they are not on top.
    */
  case object Lifted extends Trust(4)

  /**
    * User is absolutely trusted and may perform any action.
    */
  case object Absolute extends Trust(5)

  val values: immutable.IndexedSeq[Trust] = findValues
}
