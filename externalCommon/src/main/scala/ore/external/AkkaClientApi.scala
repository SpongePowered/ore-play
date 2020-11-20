package ore.external

import scala.concurrent.duration.FiniteDuration

import ore.external.AkkaClientApi.ClientSettings

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.pattern.CircuitBreaker
import akka.stream.Materializer
import akka.util.ByteString
import cats.Applicative
import com.typesafe.scalalogging.Logger
import io.circe.{Decoder, Json}
import zio._

abstract class AkkaClientApi[E[_], ErrorType](
    serviceName: String,
    counter: Ref[Long],
    settings: ClientSettings
)(
    implicit system: ActorSystem,
    mat: Materializer,
    E: Applicative[E]
) {

  implicit val jsonUnmarshaller: FromEntityUnmarshaller[Json] =
    Unmarshaller.byteStringUnmarshaller.forContentTypes(ContentTypes.`application/json`).map {
      case ByteString.empty => throw Unmarshaller.NoContentException
      case data             => io.circe.jawn.parseByteBuffer(data.asByteBuffer).fold(throw _, identity)
    }

  def createStatusError(statusCode: StatusCode, message: Option[String]): ErrorType

  protected def Logger: Logger

  protected val breaker: CircuitBreaker =
    CircuitBreaker(system.scheduler, settings.breakerMaxFailures, settings.breakerTimeoutDur, settings.breakerResetDur)

  breaker.onOpen {
    Logger.error(s"Lost connection to $serviceName. Circuit breaker opened")
  }

  private def nextCounter: UIO[Long] = counter.modify(c => (c + 1, c))

  protected def apiUri(f: Uri.Path => Uri.Path): Uri = settings.apiUri.withPath(f(settings.apiUri.path))

  private def debugF[R, E0, A](before: => String, after: A => String, fa: ZIO[R, E0, A]): ZIO[R, E0, A] = {
    if (Logger.underlying.isDebugEnabled) {
      for {
        c   <- nextCounter
        _   <- ZIO.effectTotal(Logger.debug(s"$c $before"))
        res <- fa
        _   <- ZIO.effectTotal(Logger.debug(s"$c ${after(res)}"))
      } yield res
    } else fa
  }

  protected def makeRequest(request: HttpRequest): UIO[HttpResponse] = {
    debugF(
      s"Making request: $request",
      (res: HttpResponse) => s"Request response: $res",
      ZIO.fromFuture(_ => breaker.withCircuitBreaker(Http().singleRequest(request))).orDie
    )
  }

  private def unmarshallResponse[A](
      response: HttpResponse
  )(implicit um: Unmarshaller[HttpResponse, A]): UIO[A] =
    ZIO.fromFuture(ec => Unmarshal(response).to[A](um, ec, mat)).orDie

  protected def gatherStatusErrors(response: HttpResponse): IO[E[ErrorType], HttpResponse] = {
    if (response.status.isSuccess()) ZIO.succeed(response)
    else if (response.entity.isKnownEmpty())
      ZIO.effectTotal(response.entity.discardBytes()) *> ZIO.fail(E.pure(createStatusError(response.status, None)))
    else {
      unmarshallResponse[String](response).flatMap(e => ZIO.fail(E.pure(createStatusError(response.status, Some(e)))))
    }
  }

  protected def gatherJsonErrors[A: Decoder](json: Json): Either[E[ErrorType], A]

  protected def runRequest[A: Decoder](request: HttpRequest): IO[E[ErrorType], A] = {
    val requestWithAccept =
      if (request.header[Accept].isDefined) request
      else request.withHeaders(request.headers :+ Accept(MediaRange(MediaTypes.`application/json`)))

    makeRequest(requestWithAccept)
      .flatMap(gatherStatusErrors)
      .flatMap(unmarshallResponse[Json])
      .map(gatherJsonErrors[A])
      .absolve
  }
}
object AkkaClientApi {

  trait ClientSettings {
    def apiUri: Uri
    def breakerMaxFailures: Int
    def breakerResetDur: FiniteDuration
    def breakerTimeoutDur: FiniteDuration
  }
}
