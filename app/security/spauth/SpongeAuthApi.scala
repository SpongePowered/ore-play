package security.spauth

import java.util.concurrent.TimeoutException

import com.google.common.base.Preconditions._
import javax.inject.Inject
import ore.OreConfig
import util.WSUtils.parseJson
import util.functional.{EitherT, OptionT}
import util.instances.future._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.Configuration
import play.api.i18n.Lang

/**
  * Interfaces with the SpongeAuth Web API
  */
trait SpongeAuthApi {

  /** The base URL of the instance */
  val url: String
  /** Secret API key */
  val apiKey: String
  val ws: WSClient
  val timeout: Duration = 10.seconds

  val Logger = play.api.Logger("SpongeAuth")

  implicit private val spongeUserReads: Reads[SpongeUser] = (
    (JsPath \ "id").read[Int] and
    (JsPath \ "username").read[String] and
    (JsPath \ "email").read[String] and
    (JsPath \ "avatar_url").readNullable[String] and
    (JsPath \ "language").readNullable[String].map(_.flatMap(Lang.get))
  )(SpongeUser.apply _)

  /**
    * Creates a new user with the specified credentials.
    *
    * @param username Username
    * @param email    Email
    * @param password Password (nullable)
    * @param verified True if should bypass email verification
    * @return         Newly created user
    */
  def createUser(username: String,
                 email: String,
                 password: String,
                 verified: Boolean = false)(implicit ec: ExecutionContext): EitherT[Future, String, SpongeUser]
  = doCreateUser(username, email, password, verified)

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @param verified True if should bypass email verification
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String, verified: Boolean = false)(implicit ec: ExecutionContext): EitherT[Future, String, SpongeUser]
  = doCreateUser(username, email, null, verified, dummy = true)

  private def doCreateUser(username: String,
                           email: String,
                           password: String,
                           verified: Boolean = false,
                           dummy: Boolean = false)(implicit ec: ExecutionContext): EitherT[Future, String, SpongeUser] = {
    checkNotNull(username, "null username", "")
    checkNotNull(email, "null email", "")
    var params = Map(
      "api-key" -> Seq(this.apiKey),
      "username" -> Seq(username),
      "email" -> Seq(email),
      "verified" -> Seq(verified.toString),
      "dummy" -> Seq(dummy.toString))
    if (password != null)
      params += "password" -> Seq(password)
    readUser(this.ws.url(route("/api/users")).withRequestTimeout(timeout).post(params))
  }

  /**
    * Returns the user with the specified username.
    *
    * @param username Username to lookup
    * @return         User with username
    */
  def getUser(username: String)(implicit ec: ExecutionContext): OptionT[Future, SpongeUser] = {
    checkNotNull(username, "null username", "")
    val url = route("/api/users/" + username) + s"?apiKey=$apiKey"
    readUser(this.ws.url(url).withRequestTimeout(timeout).get()).toOption
  }

  /**
    * Deletes the user with the specified username.
    *
    * @param username Username to lookup
    * @return         Deleted user
    */
  def deleteUser(username: String)(implicit ec: ExecutionContext): EitherT[Future, String, SpongeUser] = {
    checkNotNull(username, "null username", "")
    val url = route("/api/users") + s"?username=$username&apiKey=$apiKey"
    readUser(this.ws.url(url).withRequestTimeout(timeout).delete())
  }

  private def readUser(response: Future[WSResponse], nullable: Boolean = false)(implicit ec: ExecutionContext): EitherT[Future, String, SpongeUser] = {
    EitherT(
      OptionT(response.map(parseJson(_, Logger))).map { json =>
        val obj = json.as[JsObject]
        if (obj.keys.contains("error"))
          Left((obj \ "error").as[String])
        else
          Right(obj.as[SpongeUser])
      }.getOrElse(Left("error.spongeauth.parse")).recover {
        case toe: TimeoutException =>
          Left("error.spongeauth.connect")
        case e =>
          Logger.error("An unexpected error occured while handling a response", e)
          Left("error.spongeauth.unexpected")
      }
    )
  }

  private def route(route: String) = this.url + route

}

final class SpongeAuth @Inject()(config: OreConfig, override val ws: WSClient) extends SpongeAuthApi {

  val conf: Configuration = this.config.security

  override val url: String = this.conf.get[String]("api.url")
  override val apiKey: String = this.conf.get[String]("api.key")
  override val timeout: FiniteDuration = this.conf.get[FiniteDuration]("api.timeout")

}
