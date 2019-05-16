package security.spauth

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import scala.concurrent.duration._

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import ore.OreConfig
import ore.util.OreMDC
import _root_.util.WSUtils.parseJson

import cats.ApplicativeError
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.syntax.all._
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging

/**
  * Interfaces with the SpongeAuth Web API
  */
trait SpongeAuthApi {

  /** The base URL of the instance */
  def url: String

  /** Secret API key */
  def apiKey: String
  def ws: WSClient
  val timeout: Duration = 10.seconds

  private val Logger    = scalalogging.Logger("SpongeAuth")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String)(implicit mdc: OreMDC): EitherT[IO, List[String], SpongeUser] =
    doCreateUser(username, email, None)

  private def doCreateUser(
      username: String,
      email: String,
      password: Option[String],
  )(implicit mdc: OreMDC): EitherT[IO, List[String], SpongeUser] = {
    checkNotNull(username, "null username", "")
    checkNotNull(email, "null email", "")
    val params = Map(
      "api-key"  -> Seq(this.apiKey),
      "username" -> Seq(username),
      "email"    -> Seq(email),
      "verified" -> Seq(true.toString),
      "dummy"    -> Seq(true.toString)
    )

    val withPassword = password.fold(params)(pass => params + ("password" -> Seq(pass)))
    readUser(IO.fromFuture(IO(this.ws.url(route("/api/users")).withRequestTimeout(timeout).post(withPassword))))
  }

  /**
    * Returns the user with the specified username.
    *
    * @param username Username to lookup
    * @return         User with username
    */
  def getUser(username: String)(implicit mdc: OreMDC): OptionT[IO, SpongeUser] = {
    checkNotNull(username, "null username", "")
    val url = route("/api/users/" + username) + s"?apiKey=$apiKey"
    readUser(IO.fromFuture(IO(this.ws.url(url).withRequestTimeout(timeout).get()))).toOption
  }

  /**
    * Returns the signed_data that can be used to construct the change-avatar
    */
  def getChangeAvatarToken(
      requester: String,
      organization: String
  )(implicit mdc: OreMDC): EitherT[IO, String, ChangeAvatarToken] = {
    val params = Map(
      "api-key"          -> Seq(this.apiKey),
      "request_username" -> Seq(requester),
    )
    readChangeAvatarToken(
      IO.fromFuture(
        IO(
          this.ws.url(route(s"/api/users/$organization/change-avatar-token/")).withRequestTimeout(timeout).post(params)
        )
      )
    )
  }

  private def readChangeAvatarToken(
      response: IO[WSResponse]
  )(implicit mdc: OreMDC): EitherT[IO, String, ChangeAvatarToken] = {
    val parsed = OptionT(response.map(parseJson(_, MDCLogger)))
      .map(json => json.as[ChangeAvatarToken])
      .toRight("Failed to parse json response")

    ApplicativeError[EitherT[IO, String, ?], Throwable].recoverWith(parsed) {
      case _: TimeoutException =>
        EitherT.leftT("error.spongeauth.auth")
      case e =>
        MDCLogger.error("An unexpected error occured while handling a response", e)
        EitherT.leftT("error.spongeauth.unexpected")
    }
  }

  private def readUser(response: IO[WSResponse])(implicit mdc: OreMDC): EitherT[IO, List[String], SpongeUser] = {
    EitherT(
      OptionT(response.map(parseJson(_, MDCLogger)))
        .map { json =>
          val obj = json.as[JsObject]
          if (obj.keys.contains("error"))
            Left((obj \ "error").as[JsArray].value.map(_.as[String]).toList)
          else
            Right(obj.as[SpongeUser])
        }
        .getOrElse(Left(List("error.spongeauth.auth")))
        .handleError {
          case _: TimeoutException =>
            Left(List("error.spongeauth.auth"))
          case e =>
            MDCLogger.error("An unexpected error occured while handling a response", e)
            Left(List("error.spongeauth.unexpected"))
        }
    )
  }

  private def route(route: String) = this.url + route

}

final class SpongeAuth @Inject()(config: OreConfig, override val ws: WSClient) extends SpongeAuthApi {

  private val conf = this.config.security.api

  override val url: String             = conf.url
  override val apiKey: String          = conf.key
  override val timeout: FiniteDuration = conf.timeout

}

case class ChangeAvatarToken(signedData: String, targetUsername: String, requestUserId: Int)

object ChangeAvatarToken {
  implicit val changeAvatarTokenReads: Reads[ChangeAvatarToken] =
    (JsPath \ "signed_data")
      .read[String]
      .and((JsPath \ "raw_data" \ "target_username").read[String])
      .and((JsPath \ "raw_data" \ "request_user_id").read[Int])(ChangeAvatarToken.apply _)
}
