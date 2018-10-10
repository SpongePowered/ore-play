package ore.project

import scala.collection.immutable
import scala.util.Try

import enumeratum.values._

sealed abstract class Category(val value: Int, val title: String, val icon: String, val isVisible: Boolean = true)
    extends IntEnumEntry

/**
  * Enumeration of Categories a Project may possess.
  */
object Category extends IntEnum[Category] {

  val values: immutable.IndexedSeq[Category] = findValues

  case object AdminTools extends Category(0, "Admin Tools", "fa-server")
  case object Chat       extends Category(1, "Chat", "fa-comment")
  case object DevTools   extends Category(2, "Developer Tools", "fa-wrench")
  case object Econ       extends Category(3, "Economy", "fa-money")
  case object Gameplay   extends Category(4, "Gameplay", "fa-puzzle-piece")
  case object Games      extends Category(5, "Games", "fa-gamepad")
  case object Protect    extends Category(6, "Protection", "fa-lock")
  case object Rp         extends Category(7, "Role Playing", "fa-magic")
  case object WorldMgmt  extends Category(8, "World Management", "fa-globe")
  case object Misc       extends Category(9, "Miscellaneous", "fa-asterisk")
  case object Undefined  extends Category(10, "Undefined", null, isVisible = false)

  /**
    * Returns the visible Categories.
    *
    * @return Visible categories
    */
  def visible: Seq[Category] = this.values.filter(_.isVisible).sortBy(_.value)

  /**
    * Returns an Array of categories from a comma separated string of IDs.
    *
    * @param str  Comma separated string of IDs
    * @return     Array of Categories
    */
  def fromString(str: String): Array[Category] =
    str.split(",").flatMap { idStr =>
      val id = Try(idStr.toInt).getOrElse(-1)
      withValueOpt(id)
    }
}
