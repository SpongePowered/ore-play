package ore.data

import scala.collection.immutable

import ore.models.project.TagColor

import enumeratum.values._

/**
  * Colors used in Ore.
  */
sealed abstract class Color(val value: Int, val hex: String) extends IntEnumEntry {
  def toTagColor: TagColor = TagColor.withValue(value + 9)
}

object Color extends IntEnum[Color] {

  val values: immutable.IndexedSeq[Color] = findValues

  case object Purple      extends Color(0, "#B400FF")
  case object Violet      extends Color(1, "#C87DFF")
  case object Magenta     extends Color(2, "#E100E1")
  case object Blue        extends Color(3, "#0000FF")
  case object LightBlue   extends Color(4, "#B9F2FF")
  case object Quartz      extends Color(5, "#E7FEFF")
  case object Aqua        extends Color(6, "#0096FF")
  case object Cyan        extends Color(7, "#00E1E1")
  case object Green       extends Color(8, "#00DC00")
  case object DarkGreen   extends Color(9, "#009600")
  case object Chartreuse  extends Color(10, "#7FFF00")
  case object Amber       extends Color(11, "#FFC800")
  case object Gold        extends Color(12, "#CFB53B")
  case object Orange      extends Color(13, "#FF8200")
  case object Red         extends Color(14, "#DC0000")
  case object Silver      extends Color(15, "#C0C0C0")
  case object Gray        extends Color(16, "#A9A9A9")
  case object Transparent extends Color(17, "transparent")
}
