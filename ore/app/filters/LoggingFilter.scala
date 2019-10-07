package filters

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.mvc._

import com.typesafe.scalalogging.Logger

class LoggingFilter @Inject()(implicit ec: ExecutionContext) extends EssentialFilter {

  val timingsLogger = Logger("Timings")

  override def apply(nextFilter: EssentialAction): EssentialAction = (requestHeader: RequestHeader) => {
    val startTime = System.currentTimeMillis

    nextFilter(requestHeader).map { result =>
      val endTime     = System.currentTimeMillis
      val requestTime = endTime - startTime

      if (requestTime > 1000) {
        timingsLogger.warn(
          s"${requestHeader.method} ${requestHeader.uri} took ${requestTime}ms and returned ${result.header.status}"
        )
      } else {
        timingsLogger.info(
          s"${requestHeader.method} ${requestHeader.uri} took ${requestTime}ms and returned ${result.header.status}"
        )
      }

      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}
