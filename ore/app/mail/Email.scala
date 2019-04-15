package mail

import play.twirl.api.Html

/**
  * Represents an email to send.
  *
  * @param recipient  Recipient of email
  * @param subject    Subject line
  * @param content    HTML content
  */
case class Email(recipient: String, subject: String, content: Html)
