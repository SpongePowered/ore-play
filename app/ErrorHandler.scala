import javax.inject._

import scala.concurrent._

import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import ore.OreConfig

/** A custom server error handler */
class ErrorHandler @Inject()(
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

    Future.successful {
      if (exception.cause.isInstanceOf[TimeoutException])
        GatewayTimeout(views.html.errors.timeout())
      else
        InternalServerError(views.html.errors.error())
    }
  }

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    implicit val requestImpl: RequestHeader = request

    Future.successful(NotFound(views.html.errors.notFound()))
  }

}
