import javax.inject._

import scala.concurrent._

import play.api._
import play.api.http.{DefaultHttpErrorHandler, HtmlOrJsonHttpErrorHandler, JsonHttpErrorHandler}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import ore.OreConfig

import ErrorHandler.OreHttpErrorHandler

/** A custom server error handler */
class ErrorHandler @Inject()(
    httpHandler: OreHttpErrorHandler,
    jsonHandler: JsonHttpErrorHandler
) extends HtmlOrJsonHttpErrorHandler(httpHandler, jsonHandler)
object ErrorHandler {

  class OreHttpErrorHandler @Inject()(
      env: Environment,
      conf: Configuration,
      sourceMapper: OptionalSourceMapper,
      router: Provider[Router],
      val messagesApi: MessagesApi
  )(implicit config: OreConfig)
      extends DefaultHttpErrorHandler(env, conf, sourceMapper, router)
      with I18nSupport {

    override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
      implicit val requestImpl: RequestHeader = request
      implicit val flash: Flash               = request.flash

      Future.successful {
        exception.cause match {
          case _: TimeoutException => GatewayTimeout(views.html.errors.timeout())
          case _                   => InternalServerError(views.html.errors.error())
        }
      }
    }

    override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
      implicit val requestImpl: RequestHeader = request
      implicit val flash: Flash               = request.flash

      Future.successful(NotFound(views.html.errors.notFound()))
    }
  }
}
