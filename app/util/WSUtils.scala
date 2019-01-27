package util

import scala.util.{Failure, Success, Try}

import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse

import com.typesafe.scalalogging.LoggerTakingImplicit

object WSUtils {

  def parseJson[A](response: WSResponse, log: LoggerTakingImplicit[A])(implicit a: A): Option[JsValue] = {
    Try(response.json) match {
      case Failure(e) =>
        log.debug(s"Failed to parse response as JSON. Actual response body is ${response.body}", e)
        None
      case Success(json) =>
        log.debug("Response: " + json)
        Some(json)
    }
  }

}
