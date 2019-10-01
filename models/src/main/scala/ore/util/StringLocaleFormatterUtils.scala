package ore.util

import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.util.Locale

object StringLocaleFormatterUtils {

  private val dateFormat     = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

  /**
    * Formats the specified date into the standard application form.
    *
    * @param dateTime Date to format
    * @return        Standard formatted date
    */
  def prettifyDate(dateTime: OffsetDateTime)(implicit locale: Locale): String =
    dateFormat.withLocale(locale).format(dateTime)

  /**
    * Formats the specified date into the standard application form time.
    *
    * @param dateTime Date to format
    * @return        Standard formatted date
    */
  def prettifyDateAndTime(dateTime: OffsetDateTime)(implicit locale: Locale): String =
    dateTimeFormat.withLocale(locale).format(dateTime)
}
