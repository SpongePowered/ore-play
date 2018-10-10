package security.spauth

import java.math.BigInteger
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

import play.api.Configuration
import play.api.http.Status
import play.api.i18n.Lang
import play.api.libs.ws.WSClient

import security.CryptoUtils

import akka.http.scaladsl.model.Uri
import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._

/**
  * Manages authentication to Sponge services.
  */
trait SingleSignOnConsumer {

  def ws: WSClient
  def loginUrl: String
  def signupUrl: String
  def verifyUrl: String
  def secret: String
  def timeout: Duration

  val CharEncoding = "UTF-8"
  val Algo         = "HmacSHA256"

  val Logger = play.api.Logger("SSO")

  /**
    * Returns a future result of whether SSO is available.
    *
    * @return True if available
    */
  def isAvailable(implicit ec: ExecutionContext): Boolean =
    Await.result(this.ws.url(this.loginUrl).get().map(_.status == Status.OK).recover {
      case _: Exception => false
    }, this.timeout)

  /**
    * Returns the login URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getLoginUrl(returnUrl: String, nonce: String): String = getUrl(returnUrl, this.loginUrl, nonce)

  /**
    * Returns the signup URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getSignupUrl(returnUrl: String, nonce: String): String = getUrl(returnUrl, this.signupUrl, nonce)

  /**
    * Returns the verify URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getVerifyUrl(returnUrl: String, nonce: String): String = getUrl(returnUrl, this.verifyUrl, nonce)

  private def getUrl(returnUrl: String, baseUrl: String, nonce: String) = {
    val payload    = generatePayload(returnUrl, nonce)
    val sig        = generateSignature(payload)
    val urlEncoded = URLEncoder.encode(payload, this.CharEncoding)
    baseUrl + "?sso=" + urlEncoded + "&sig=" + sig
  }

  /**
    * Generates a new Base64 encoded SSO payload.
    *
    * @param returnUrl  URL to return to once authenticated
    * @return           New payload
    */
  def generatePayload(returnUrl: String, nonce: String): String = {
    val payload = "return_sso_url=" + returnUrl + "&nonce=" + nonce
    new String(Base64.getEncoder.encode(payload.getBytes(this.CharEncoding)))
  }

  /**
    * Generates a signature for the specified Base64 encoded payload.
    *
    * @param payload  Payload to sign
    * @return         Signature of payload
    */
  def generateSignature(payload: String): String = CryptoUtils.hmac_sha256(secret, payload.getBytes(this.CharEncoding))

  /**
    * Validates an incoming payload and extracts user information. The
    * incoming payload indicates that the User was authenticated successfully
    * off-site.
    *
    * @param payload        Incoming SSO payload
    * @param sig            Incoming SSO signature
    * @param isNonceValid   Callback to check if an incoming nonce is valid and
    *                       marks the nonce as invalid so it cannot be used again
    * @return               [[SpongeUser]] if successful
    */
  def authenticate(payload: String, sig: String)(
      isNonceValid: String => Future[Boolean]
  )(implicit ec: ExecutionContext): OptionT[Future, SpongeUser] = {
    Logger.debug("Authenticating SSO payload...")
    Logger.debug(payload)
    Logger.debug("Signed with : " + sig)
    if (generateSignature(payload) != sig) {
      Logger.debug("<FAILURE> Could not verify payload against signature.")
      return OptionT.none[Future, SpongeUser]
    }

    // decode payload
    val query = Uri.Query(Base64.getMimeDecoder.decode(payload))
    Logger.debug("Decoded payload:")
    Logger.debug(query.toString())

    // extract info
    val info = for {
      nonce      <- query.get("nonce")
      externalId <- query.get("external_id").flatMap(s => Try(s.toLong).toOption)
      username   <- query.get("username")
      email      <- query.get("email")
    } yield {
      nonce -> SpongeUser(
        externalId,
        username,
        email,
        query.get("avatar_url"),
        query.get("language").flatMap(Lang.get),
        query.get("add_groups")
      )
    }

    OptionT
      .fromOption[Future](info)
      .semiflatMap { case (nonce, user) => isNonceValid(nonce).tupleRight(user) }
      .subflatMap {
        case (false, _) =>
          Logger.debug("<FAILURE> Invalid nonce.")
          None
        case (true, user) =>
          Logger.debug("<SUCCESS> " + user)
          Some(user)
      }
  }
}

object SingleSignOnConsumer {

  val Random = new SecureRandom

  def nonce: String = new BigInteger(130, Random).toString(32)
}

class SpongeSingleSignOnConsumer @Inject()(val ws: WSClient, config: Configuration) extends SingleSignOnConsumer {

  private val conf = this.config.get[Configuration]("security")

  override val loginUrl: String        = this.conf.get[String]("sso.loginUrl")
  override val signupUrl: String       = this.conf.get[String]("sso.signupUrl")
  override val verifyUrl: String       = this.conf.get[String]("sso.verifyUrl")
  override val secret: String          = this.conf.get[String]("sso.secret")
  override val timeout: FiniteDuration = this.conf.get[FiniteDuration]("sso.timeout")

}
