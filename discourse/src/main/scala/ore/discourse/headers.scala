package ore.discourse

import scala.util.{Success, Try}

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}

case class `Api-Key`(key: String) extends ModeledCustomHeader[`Api-Key`] {
  override def companion: ModeledCustomHeaderCompanion[`Api-Key`] = `Api-Key`

  override def value: String              = key
  override def renderInRequests: Boolean  = true
  override def renderInResponses: Boolean = false
}
object `Api-Key` extends ModeledCustomHeaderCompanion[`Api-Key`] {
  override def name: String                         = "Api-Key"
  override def parse(value: String): Try[`Api-Key`] = Success(new `Api-Key`(value))
}

case class `Api-Username`(username: String) extends ModeledCustomHeader[`Api-Username`] {
  override def companion: ModeledCustomHeaderCompanion[`Api-Username`] = `Api-Username`

  override def value: String              = username
  override def renderInRequests: Boolean  = true
  override def renderInResponses: Boolean = false
}
object `Api-Username` extends ModeledCustomHeaderCompanion[`Api-Username`] {
  override def name: String                              = "Api-Username"
  override def parse(value: String): Try[`Api-Username`] = Success(new `Api-Username`(value))
}
