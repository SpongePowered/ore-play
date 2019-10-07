package ore.discourse

import scala.concurrent.duration.FiniteDuration

import akka.http.scaladsl.model.StatusCode

sealed trait DiscourseError
object DiscourseError {
  case class RatelimitError(waitTime: FiniteDuration)                                      extends DiscourseError
  case class UnknownError(messages: Seq[String], tpe: String, extras: Map[String, String]) extends DiscourseError
  case class StatusError(statusCode: StatusCode, message: Option[String])                  extends DiscourseError
  case object NotAvailable                                                                 extends DiscourseError
}
