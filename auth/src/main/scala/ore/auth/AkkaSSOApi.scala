package ore.auth

import scala.language.higherKinds

import java.net.URLEncoder
import java.security.SecureRandom
import java.util.{Base64, Locale}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.Materializer
import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.syntax.all._
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.all._
import com.typesafe.scalalogging

class AkkaSSOApi[F[_]](
    loginUrl: String,
    signupUrl: String,
    verifyUrl: String,
    secret: String,
    timeout: FiniteDuration,
    reset: FiniteDuration,
    cachedAvailable: Ref[F, Option[Boolean]]
)(implicit F: Concurrent[F], cs: ContextShift[F], timer: Timer[F], system: ActorSystem, mat: Materializer)
    extends SSOApi[F] {

  private val Logger = scalalogging.Logger("SSO")

  private val CharEncoding = "UTF-8"
  private val rand         = new SecureRandom

  private def futureToF[A](future: => Future[A]) = {
    import system.dispatcher
    F.async[A] { callback =>
      future.onComplete(t => callback(t.toEither))
    }
  }

  override def isAvailable: F[Boolean] = {
    cachedAvailable.access.flatMap {
      case (Some(available), _) => available.pure
      case (None, setter) =>
        val ssoTestRequest =
          futureToF(Http().singleRequest(HttpRequest(HttpMethods.HEAD, loginUrl)))
            .flatTap(r => F.delay(r.discardEntityBytes()))
            .map(_.status.isSuccess())
            .timeoutTo(timeout, false.pure)

        ssoTestRequest.flatMap { result =>
          val scheduleReset = cs.shift *> timer.sleep(reset) *> cachedAvailable.set(None)

          setter(Some(result))
            .flatMap {
              case true  => scheduleReset
              case false => F.unit
            }
            .as(result)
        }
    }
  }

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

  override def authenticate(payload: String, sig: String)(isNonceValid: String => F[Boolean]): F[Option[AuthUser]] = {
    Logger.debug("Authenticating SSO payload...")
    Logger.debug(payload)
    Logger.debug("Signed with : " + sig)

    if (generateSignature(payload) != sig) {
      Logger.debug("<FAILURE> Could not verify payload against signature.")
      F.pure(None)
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

      OptionT
        .fromOption[F](info)
        .semiflatMap { case (nonce, user) => isNonceValid(nonce).tupleRight(user) }
        .subflatMap {
          case (false, _) =>
            Logger.debug("<FAILURE> Invalid nonce.")
            None
          case (true, user) =>
            Logger.debug("<SUCCESS> " + user)
            Some(user)
        }
        .value
    }
  }
}
object AkkaSSOApi {
  def apply[F[_]](
      loginUrl: String,
      signupUrl: String,
      verifyUrl: String,
      secret: String,
      timeout: FiniteDuration,
      reset: FiniteDuration
  )(
      implicit F: Concurrent[F],
      cs: ContextShift[F],
      timer: Timer[F],
      system: ActorSystem,
      mat: Materializer
  ): F[AkkaSSOApi[F]] =
    Ref
      .of[F, Option[Boolean]](None)
      .map(ref => new AkkaSSOApi[F](loginUrl, signupUrl, verifyUrl, secret, timeout, reset, ref))
}
