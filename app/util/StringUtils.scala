package util

import java.nio.file.{Files, Path}
import java.text.{MessageFormat, SimpleDateFormat}
import java.util.Date

import db.impl.OrePostgresDriver.api._
import ore.OreConfig

/**
  * Helper class for handling User input.
  */
object StringUtils {

  /**
    * Formats the specified date into the standard application form.
    *
    * @param date Date to format
    * @return     Standard formatted date
    */
  def prettifyDate(date: Date)(implicit config: OreConfig): String
  = new SimpleDateFormat(config.ore.getString("date-format").get).format(date)

  /**
    * Returns a URL readable string from the specified string.
    *
    * @param str  String to create slug for
    * @return     Slug of string
    */
  def slugify(str: String): String = compact(str).replace(' ', '-')

  /**
    * Returns the specified String with all consecutive spaces removed.
    *
    * @param str  String to compact
    * @return     Compacted string
    */
  def compact(str: String): String = str.trim.replaceAll(" +", " ")

  /**
    * Returns null if the specified String is empty, returns the trimmed string
    * otherwise.
    *
    * @param str String to check
    * @return Null if empty, trimmed otherwise
    */
  def nullIfEmpty(str: String): String = {
    val trimmed = str.trim
    if (trimmed.nonEmpty) trimmed else null
  }

  /**
    * Compares a Rep[String] to a String after converting them to lower case.
    *
    * @param str1 String 1
    * @param str2 String 2
    * @return     Result
    */
  def equalsIgnoreCase[T <: Table[_]](str1: T => Rep[String], str2: String): T => Rep[Boolean]
  = str1(_).toLowerCase === str2.toLowerCase

  /**
    * Reads the specified Path's file content and formats it with the
    * specified parameters.
    *
    * @param path   Path to file
    * @param params Format parameters
    * @return       Formatted string
    */
  def readAndFormatFile(path: Path, params: String*): String
  = MessageFormat.format(new String(Files.readAllBytes(path)), params.map(_.asInstanceOf[AnyRef]): _*)

  /**
    * Formats the specified date into the standard application form time.
    *
    * @param date Date to format
    * @return     Standard formatted date
    */
  def prettifyDateAndTime(date: Date)(implicit config: OreConfig): String
  = new SimpleDateFormat(config.ore.getString("date-and-time-format").get).format(date)
}
