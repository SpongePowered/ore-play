package security.spauth

import java.math.BigInteger
import java.net.{URLDecoder, URLEncoder}
import java.security.SecureRandom
import java.util.Base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import org.apache.commons.codec.binary.Hex
import play.api.Configuration
import play.api.http.Status
import play.api.libs.ws.WSClient
import util.functional.OptionT
import util.instances.future._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Manages authentication to Sponge services.
  */
trait SingleSignOnConsumer {

  val ws: WSClient
  val loginUrl: String
  val signupUrl: String
  val verifyUrl: String
  val secret: String
  val timeout: Duration

  val CharEncoding = "UTF-8"
  val Algo = "HmacSHA256"

  val Logger = play.api.Logger("SSO")

  /**
    * Returns a future result of whether SSO is available.
    *
    * @return True if available
    */
  def isAvailable(implicit ec: ExecutionContext): Boolean = Await.result(this.ws.url(this.loginUrl).get().map(_.status == Status.OK).recover {
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
    val payload = generatePayload(returnUrl, baseUrl, nonce)
    val sig = generateSignature(payload)
    val urlEncoded = URLEncoder.encode(payload, this.CharEncoding)
    baseUrl + "?sso=" + urlEncoded + "&sig=" + sig
  }

  /**
    * Generates a new Base64 encoded SSO payload.
    *
    * @param returnUrl  URL to return to once authenticated
    * @param baseUrl    Base URL
    * @return           New payload
    */
  def generatePayload(returnUrl: String, baseUrl: String, nonce: String) = {
    val payload = "return_sso_url=" + returnUrl + "&nonce=" + nonce
    new String(Base64.getEncoder.encode(payload.getBytes(this.CharEncoding)))
  }

  /**
    * Generates a signature for the specified Base64 encoded payload.
    *
    * @param payload  Payload to sign
    * @return         Signature of payload
    */
  def generateSignature(payload: String) = hmac_sha256(payload.getBytes(this.CharEncoding))

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
  def authenticate(payload: String, sig: String)(isNonceValid: String => Future[Boolean])(implicit ec: ExecutionContext): OptionT[Future, SpongeUser] = {
    Logger.info("Authenticating SSO payload...")
    Logger.info(payload)
    Logger.info("Signed with : " + sig)
    if (!hmac_sha256(payload.getBytes(this.CharEncoding)).equals(sig)) {
      Logger.info("<FAILURE> Could not verify payload against signature.")
      return OptionT.none[Future, SpongeUser]
    }

    // decode payload
    val decoded = URLDecoder.decode(new String(Base64.getMimeDecoder.decode(payload)), this.CharEncoding)
    Logger.info("Decoded payload:")
    Logger.info(decoded)

    // extract info
    val params = decoded.split('&')
    var nonce: String = null
    var externalId: Int = -1
    var username: String = null
    var email: String = null
    var avatarUrl: String = null

    for (param <- params) {
      val data = param.split('=')
      val value = if (data.length > 1) data(1) else null
      data(0) match {
        case "nonce" => nonce = value
        case "external_id" => externalId = Integer.parseInt(value)
        case "username" => username = value
        case "email" => email = value
        case "avatar_url" => avatarUrl = value
        case _ =>
      }
    }

    if (externalId == -1 || username == null || email == null || nonce == null) {
      Logger.info("<FAILURE> Incomplete payload.")
      return OptionT.none[Future, SpongeUser]
    }

    OptionT.liftF(isNonceValid(nonce)).subflatMap {
      case false =>
        Logger.info("<FAILURE> Invalid nonce.")
        None
      case true =>
        val user = SpongeUser(externalId, username, email, Option(avatarUrl))
        Logger.info("<SUCCESS> " + user)
        Some(user)
    }
  }

  private def hmac_sha256(data: Array[Byte]): String = {
    val hmac = Mac.getInstance(this.Algo)
    val keySpec = new SecretKeySpec(this.secret.getBytes(this.CharEncoding), this.Algo)
    hmac.init(keySpec)
    Hex.encodeHexString(hmac.doFinal(data))
  }

}

object SingleSignOnConsumer {

  val Random = new SecureRandom

  def nonce: String = new BigInteger(130, Random).toString(32)

}

class SpongeSingleSignOnConsumer @Inject()(override val ws: WSClient, config: Configuration) extends SingleSignOnConsumer {

  private val conf = this.config.get[Configuration]("security")

  override val loginUrl = this.conf.get[String]("sso.loginUrl")
  override val signupUrl = this.conf.get[String]("sso.signupUrl")
  override val verifyUrl = this.conf.get[String]("sso.verifyUrl")
  override val secret = this.conf.get[String]("sso.secret")
  override val timeout = this.conf.get[FiniteDuration]("sso.timeout")

}
