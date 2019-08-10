package ore.auth

import scala.language.higherKinds

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import ore.auth.AkkaSpongeAuthApi.AkkaSpongeAuthSettings
import ore.external.AkkaClientApi

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.all._
import com.typesafe.scalalogging
import io.circe.{Decoder, Json}

class AkkaSpongeAuthApi[F[_]] private (
    settings: AkkaSpongeAuthSettings,
    counter: Ref[F, Long]
)(
    implicit system: ActorSystem,
    mat: Materializer,
    F: Concurrent[F]
) extends AkkaClientApi[F, List, String]("SpongeAuth", counter, settings)
    with SpongeAuthApi[F] {

  protected val Logger = scalalogging.Logger("SpongeAuth")

  protected def gatherJsonErrors[A: Decoder](json: Json): Either[List[String], A] = {
    val error = json.hcursor.downField("error")
    if (error.succeeded)
      Left(error.as[List[String]].getOrElse(List("No error message found")))
    else
      json.as[A].leftMap(e => List(e.show))
  }

  override def createStatusError(statusCode: StatusCode, message: Option[String]): String =
    s"SpongeAuth request failed. Response code $statusCode${message.fold("")(s => s": $s")}"

  override def createDummyUser(username: String, email: String): F[Either[List[String], AuthUser]] = {
    val params = Seq(
      "api-key"  -> settings.apiKey,
      "username" -> username,
      "email"    -> email,
      "verified" -> true.toString,
      "dummy"    -> true.toString
    )

    makeUnmarshallRequestEither(
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "api" / "users"),
        entity = FormData(params: _*).toEntity
      )
    )
  }

  override def getUser(username: String): F[Either[List[String], AuthUser]] = {
    makeUnmarshallRequestEither(
      HttpRequest(
        HttpMethods.GET,
        apiUri(_ / "api" / "users" / username).withQuery(Uri.Query("apiKey" -> settings.apiKey))
      )
    )
  }

  private def getChangeAvatarToken(
      requester: String,
      organization: String
  ): F[Either[List[String], ChangeAvatarToken]] = {
    val params = Seq(
      "api-key"          -> settings.apiKey,
      "request_username" -> requester
    )

    makeUnmarshallRequestEither(
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "api" / "users" / organization / "change-avatar-token" ++ Uri.Path.SingleSlash),
        entity = FormData(params: _*).toEntity
      )
    )
  }

  override def changeAvatarUri(requester: String, organization: String): F[Either[List[String], Uri]] =
    getChangeAvatarToken(requester, organization).map {
      _.map { token =>
        apiUri(_ / "accounts" / "user" / organization / "change-avatar" ++ Uri.Path.SingleSlash)
          .withQuery(Uri.Query("key" -> token.signedData))
      }
    }
}
object AkkaSpongeAuthApi {

  def apply[F[_]](
      settings: AkkaSpongeAuthSettings
  )(
      implicit system: ActorSystem,
      mat: Materializer,
      F: Concurrent[F]
  ): F[AkkaSpongeAuthApi[F]] =
    Ref.of[F, Long](0L).map(counter => new AkkaSpongeAuthApi(settings, counter))

  case class AkkaSpongeAuthSettings(
      apiKey: String,
      apiUri: Uri,
      breakerMaxFailures: Int,
      breakerResetDur: FiniteDuration,
      breakerTimeoutDur: FiniteDuration
  ) extends AkkaClientApi.ClientSettings

  type LazyFuture[A] = () => Future[A]
}
