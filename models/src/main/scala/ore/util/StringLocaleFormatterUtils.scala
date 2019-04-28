package ore.util

import java.text.DateFormat
import java.time.Instant
import java.util.Locale

object StringLocaleFormatterUtils {

  /**
    * Formats the specified date into the standard application form.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDate(instant: Instant)(implicit locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(instant)

  /**
    * Formats the specified date into the standard application form time.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDateAndTime(instant: Instant)(implicit locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale).format(instant)
}
