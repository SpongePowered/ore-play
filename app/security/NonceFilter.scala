package security

import java.security.SecureRandom
import java.util.Base64

import akka.stream.Materializer
import javax.inject.Inject
import play.api.libs.typedmap.TypedKey
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NonceFilter {

  val NonceKey: TypedKey[String] = TypedKey("nonce")

  def nonce(implicit request: RequestHeader): String = {
    if (request != null) {
      request.attrs.get(NonceKey).getOrElse("")
    } else {
      ""
    }
  }

}

class NonceFilter @Inject() (implicit val mat: Materializer) extends Filter {

  private val random = new SecureRandom()

  override def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    val nonce = generateNonce
    next(request.addAttr(NonceFilter.NonceKey, nonce)).map { result =>
      result.withHeaders("Content-Security-Policy" -> result.header.headers("Content-Security-Policy")
        .replace("%NONCE-SOURCE%", s"nonce-$nonce"))
    }
  }

  private def generateNonce: String = {
    val bytes = new Array[Byte](16) // 128 bit, see https://w3c.github.io/webappsec-csp/#security-nonces
    random.nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }

}
