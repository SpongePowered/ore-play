package ore.util

import java.time.format.{DateTimeFormatter, FormatStyle}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.Locale

object StringLocaleFormatterUtils {

  private val dateFormat     = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

  /**
    * Formats the specified date into the standard application form.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDate(instant: Instant)(implicit locale: Locale): String =
    dateFormat.withLocale(locale).format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC))

  /**
    * Formats the specified date into the standard application form time.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDateAndTime(instant: Instant)(implicit locale: Locale): String =
    dateTimeFormat.withLocale(locale).format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
}
