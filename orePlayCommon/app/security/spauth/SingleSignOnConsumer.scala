package security.spauth

import java.math.BigInteger
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

import scala.concurrent.duration._
import scala.util.Try

import play.api.http.Status
import play.api.i18n.Lang
import play.api.libs.ws.WSClient

import ore.OreConfig
import ore.util.OreMDC
import security.CryptoUtils

import akka.http.scaladsl.model.Uri
import cats.data.OptionT
import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.all._
import com.typesafe.scalalogging

/**
  * Manages authentication to Sponge services.
  */
trait SingleSignOnConsumer {

  def ws: WSClient
  def loginUrl: String
  def signupUrl: String
  def verifyUrl: String
  def secret: String
  def timeout: FiniteDuration

  val CharEncoding = "UTF-8"
  val Algo         = "HmacSHA256"

  private val Logger    = scalalogging.Logger("SSO")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  /**
    * Returns a future result of whether SSO is available.
    *
    * @return True if available
    */
  def isAvailable(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[Boolean] =
    IO.fromFuture(IO(this.ws.url(this.loginUrl).get())).map(_.status == Status.OK).timeoutTo(timeout, IO.pure(false))

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
      isNonceValid: String => IO[Boolean]
  )(implicit mdc: OreMDC): OptionT[IO, SpongeUser] = {
    MDCLogger.debug("Authenticating SSO payload...")
    MDCLogger.debug(payload)
    MDCLogger.debug("Signed with : " + sig)
    if (generateSignature(payload) != sig) {
      MDCLogger.debug("<FAILURE> Could not verify payload against signature.")
      OptionT.none[IO, SpongeUser]
    } else {
      // decode payload
      val query = Uri.Query(Base64.getMimeDecoder.decode(payload))
      MDCLogger.debug("Decoded payload:")
      MDCLogger.debug(query.toString())

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
        .fromOption[IO](info)
        .semiflatMap { case (nonce, user) => isNonceValid(nonce).tupleRight(user) }
        .subflatMap {
          case (false, _) =>
            MDCLogger.debug("<FAILURE> Invalid nonce.")
            None
          case (true, user) =>
            MDCLogger.debug("<SUCCESS> " + user)
            Some(user)
        }
    }
  }
}

object SingleSignOnConsumer {

  val Random = new SecureRandom

  def nonce: String = new BigInteger(130, Random).toString(32)
}

class SpongeSingleSignOnConsumer @Inject()(val ws: WSClient, config: OreConfig) extends SingleSignOnConsumer {

  private val conf = this.config.security.sso

  override val loginUrl: String        = conf.loginUrl
  override val signupUrl: String       = conf.signupUrl
  override val verifyUrl: String       = conf.verifyUrl
  override val secret: String          = conf.secret
  override val timeout: FiniteDuration = conf.timeout

}
