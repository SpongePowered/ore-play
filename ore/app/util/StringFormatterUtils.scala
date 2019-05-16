package util

import java.time.{Instant, LocalDateTime}

import play.api.i18n.Messages

import ore.util.StringLocaleFormatterUtils

object StringFormatterUtils {

  /**
    * Formats the specified date into the standard application form.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDate(instant: Instant)(implicit messages: Messages): String =
    StringLocaleFormatterUtils.prettifyDate(instant)(messages.lang.locale)

  /**
    * Formats the specified date into the standard application form time.
    *
    * @param instant Date to format
    * @return        Standard formatted date
    */
  def prettifyDateAndTime(instant: Instant)(implicit messages: Messages): String =
    StringLocaleFormatterUtils.prettifyDateAndTime(instant)(messages.lang.locale)

  def localDateTime2Instant(date: LocalDateTime, timeZone: String): Instant =
    StringLocaleFormatterUtils.localDateTime2Instant(date, timeZone)
}
