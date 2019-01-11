package util

import scala.util.{Failure, Success, Try}

import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse

import com.typesafe.scalalogging.LoggerTakingImplicit

object WSUtils {

  def parseJson[A](response: WSResponse, log: LoggerTakingImplicit[A])(implicit a: A): Option[JsValue] = {
    if (response.status < 200 || response.status >= 300)
      None
    else
      Try(response.json) match {
        case Failure(e) =>
          log.warn("Failed to parse response as JSON", e)
          None
        case Success(json) =>
          log.info("Response: " + json)
          Some(json)
      }
  }

}
