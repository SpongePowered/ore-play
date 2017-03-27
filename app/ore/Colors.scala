package ore

import db.impl.OrePostgresDriver
import db.table.MappedType
import slick.jdbc.JdbcType

import scala.language.implicitConversions

/**
  * Collection of colors used in Ore.
  */
object Colors extends Enumeration {

  val Purple      =   Color(0,    "#B400FF")
  val Violet      =   Color(1,    "#C87DFF")
  val Magenta     =   Color(2,    "#E100E1")
  val Blue        =   Color(3,    "#0000FF")
  val LightBlue   =   Color(4,    "#B9F2FF")
  val Quartz      =   Color(5,    "#E7FEFF")
  val Aqua        =   Color(6,    "#0096FF")
  val Cyan        =   Color(7,    "#00E1E1")
  val Green       =   Color(8,    "#00DC00")
  val DarkGreen   =   Color(9,    "#009600")
  val Chartreuse  =   Color(10,   "#7FFF00")
  val Amber       =   Color(11,   "#FFC800")
  val Gold        =   Color(12,   "#CFB53B")
  val Orange      =   Color(13,   "#FF8200")
  val Red         =   Color(14,   "#DC0000")
  val Silver      =   Color(15,   "#C0C0C0")
  val Gray        =   Color(16,   "#A9A9A9")
  val Transparent =   Color(17,   "transparent")

  /** Represents a color. */
  case class Color(i: Int, hex: String) extends super.Val(i, hex) with MappedType[Color] {
    implicit val mapper: JdbcType[Color] = OrePostgresDriver.api.colorTypeMapper
  }
  implicit def convert(value: Value): Color = value.asInstanceOf[Color]

}
