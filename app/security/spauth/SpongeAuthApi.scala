package security.spauth

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import scala.concurrent.duration._

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import ore.OreConfig
import _root_.util.WSUtils.parseJson

import cats.ApplicativeError
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.syntax.all._
import com.google.common.base.Preconditions._

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

  val Logger = play.api.Logger("SpongeAuth")

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String): EitherT[IO, String, SpongeUser] =
    doCreateUser(username, email, None)

  private def doCreateUser(
      username: String,
      email: String,
      password: Option[String],
  ): EitherT[IO, String, SpongeUser] = {
    checkNotNull(username, "null username", "")
    checkNotNull(email, "null email", "")
    val params = Map(
      "api-key"  -> Seq(this.apiKey),
      "username" -> Seq(username),
      "email"    -> Seq(email),
      "verified" -> Seq(false.toString),
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
  def getUser(username: String): OptionT[IO, SpongeUser] = {
    checkNotNull(username, "null username", "")
    val url = route("/api/users/" + username) + s"?apiKey=$apiKey"
    readUser(IO.fromFuture(IO(this.ws.url(url).withRequestTimeout(timeout).get()))).toOption
  }

  /**
    * Returns the signed_data that can be used to construct the change-avatar
    * @param username
    * @param ec
    * @return
    */
  def getChangeAvatarToken(
      requester: String,
      organization: String
  )(): EitherT[IO, String, ChangeAvatarToken] = {
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

  private def readChangeAvatarToken(response: IO[WSResponse]): EitherT[IO, String, ChangeAvatarToken] = {
    val parsed = OptionT(response.map(parseJson(_, Logger)))
      .map(json => json.as[ChangeAvatarToken])
      .toRight("Failed to parse json response")

    ApplicativeError[EitherT[IO, String, ?], Throwable].recoverWith(parsed) {
      case _: TimeoutException =>
        EitherT.leftT("error.spongeauth.auth")
      case e =>
        Logger.error("An unexpected error occured while handling a response", e)
        EitherT.leftT("error.spongeauth.unexpected")
    }
  }

  private def readUser(response: IO[WSResponse]): EitherT[IO, String, SpongeUser] = {
    EitherT(
      OptionT(response.map(parseJson(_, Logger)))
        .map { json =>
          val obj = json.as[JsObject]
          if (obj.keys.contains("error"))
            Left((obj \ "error").as[String])
          else
            Right(obj.as[SpongeUser])
        }
        .getOrElse(Left("error.spongeauth.auth"))
        .handleError {
          case _: TimeoutException =>
            Left("error.spongeauth.auth")
          case e =>
            Logger.error("An unexpected error occured while handling a response", e)
            Left("error.spongeauth.unexpected")
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
