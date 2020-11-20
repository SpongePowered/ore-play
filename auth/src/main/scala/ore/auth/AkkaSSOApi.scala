package ore.auth

import java.net.URLEncoder
import java.security.SecureRandom
import java.util.{Base64, Locale}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.Materializer
import com.typesafe.scalalogging
import zio.clock.Clock
import zio.{IO, UIO, URIO, ZIO}

class AkkaSSOApi(
    loginUrl: String,
    signupUrl: String,
    verifyUrl: String,
    secret: String,
    cachedAvailable: UIO[Boolean]
) extends SSOApi {

  private val Logger = scalalogging.Logger("SSO")

  private val CharEncoding = "UTF-8"
  private val rand         = new SecureRandom

  override def isAvailable: UIO[Boolean] = cachedAvailable

  private def nonce(): String = BigInt(130, rand).toString(32)

  override def getLoginUrl(returnUrl: String): URLWithNonce = getUrl(returnUrl, loginUrl)

  override def getSignupUrl(returnUrl: String): URLWithNonce = getUrl(returnUrl, signupUrl)

  override def getVerifyUrl(returnUrl: String): URLWithNonce = getUrl(returnUrl, verifyUrl)

  private def getUrl(returnUrl: String, baseUrl: String) = {
    val generatedNonce = nonce()
    val payload        = generatePayload(returnUrl, generatedNonce)
    val sig            = generateSignature(payload)
    val urlEncoded     = URLEncoder.encode(payload, CharEncoding)
    URLWithNonce(s"$baseUrl?sso=$urlEncoded&sig=$sig", generatedNonce)
  }

  /**
    * Generates a new Base64 encoded SSO payload.
    *
    * @param returnUrl  URL to return to once authenticated
    * @return           New payload
    */
  private def generatePayload(returnUrl: String, nonce: String): String = {
    val payload = s"return_sso_url=$returnUrl&nonce=$nonce"
    new String(Base64.getEncoder.encode(payload.getBytes(CharEncoding)))
  }

  /**
    * Generates a signature for the specified Base64 encoded payload.
    *
    * @param payload  Payload to sign
    * @return         Signature of payload
    */
  private def generateSignature(payload: String): String =
    CryptoUtils.hmac_sha256(secret, payload.getBytes(this.CharEncoding))

  override def authenticate(payload: String, sig: String)(
      isNonceValid: String => UIO[Boolean]
  ): IO[Option[Nothing], AuthUser] = {
    Logger.debug("Authenticating SSO payload...")
    Logger.debug(payload)
    Logger.debug("Signed with : " + sig)

    if (generateSignature(payload) != sig) {
      Logger.debug("<FAILURE> Could not verify payload against signature.")
      ZIO.fail(None)
    } else {
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
        nonce -> AuthUser(
          externalId,
          username,
          email,
          query.get("avatar_url"),
          query.get("language").map(Locale.forLanguageTag),
          query.get("add_groups")
        )
      }

      ZIO
        .fromOption(info)
        .flatMap { case (nonce, user) => isNonceValid(nonce).map(_ -> user) }
        .flatMap {
          case (false, _) =>
            Logger.debug("<FAILURE> Invalid nonce.")
            ZIO.fail(None)
          case (true, user) =>
            Logger.debug("<SUCCESS> " + user)
            ZIO.succeed(user)
        }
    }
  }
}
object AkkaSSOApi {
  def apply(
      loginUrl: String,
      signupUrl: String,
      verifyUrl: String,
      secret: String,
      timeout: FiniteDuration,
      reset: FiniteDuration
  )(
      implicit
      system: ActorSystem,
      mat: Materializer
  ): URIO[Clock, AkkaSSOApi] = {
    val cachedIsAvailable =
      ZIO
        .fromFuture(_ => Http().singleRequest(HttpRequest(HttpMethods.HEAD, loginUrl)))
        .tap(r => ZIO.effectTotal(r.discardEntityBytes()))
        .map(_.status.isSuccess())
        .timeout(timeout.toJava)
        .someOrElse(false)
        .option
        .someOrElse(false)
        .cached(reset.toJava)

    cachedIsAvailable.map(isAvailable => new AkkaSSOApi(loginUrl, signupUrl, verifyUrl, secret, isAvailable))
  }
}
