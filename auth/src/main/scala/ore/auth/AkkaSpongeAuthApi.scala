package ore.auth

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import ore.auth.AkkaSpongeAuthApi.AkkaSpongeAuthSettings
import ore.external.AkkaClientApi

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.syntax.all._
import com.typesafe.scalalogging
import com.typesafe.scalalogging.Logger
import io.circe.{Decoder, Json}
import zio.{IO, Ref, UIO}

class AkkaSpongeAuthApi private (
    settings: AkkaSpongeAuthSettings,
    counter: Ref[Long]
)(
    implicit system: ActorSystem,
    mat: Materializer
) extends AkkaClientApi[List, String]("SpongeAuth", counter, settings)
    with SpongeAuthApi {

  protected val Logger: Logger = scalalogging.Logger("SpongeAuth")

  protected def gatherJsonErrors[A: Decoder](json: Json): Either[List[String], A] = {
    val error = json.hcursor.downField("error")
    if (error.succeeded)
      Left(error.as[List[String]].getOrElse(List("No error message found")))
    else
      json.as[A].leftMap(e => List(e.show))
  }

  override def createStatusError(statusCode: StatusCode, message: Option[String]): String =
    s"SpongeAuth request failed. Response code $statusCode${message.fold("")(s => s": $s")}"

  override def createDummyUser(username: String, email: String): IO[List[String], AuthUser] = {
    runRequest[AuthUser](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "api" / "users"),
        entity = FormData(
          "api-key"  -> settings.apiKey,
          "username" -> username,
          "email"    -> email,
          "verified" -> true.toString,
          "dummy"    -> true.toString
        ).toEntity
      )
    )
  }

  override def getUser(username: String): IO[List[String], AuthUser] = {
    runRequest[AuthUser](
      HttpRequest(
        HttpMethods.GET,
        apiUri(_ / "api" / "users" / username).withQuery(Uri.Query("apiKey" -> settings.apiKey))
      )
    )
  }

  private def getChangeAvatarToken(
      requester: String,
      organization: String
  ): IO[List[String], ChangeAvatarToken] = {
    runRequest[ChangeAvatarToken](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "api" / "users" / organization / "change-avatar-token" ++ Uri.Path.SingleSlash),
        entity = FormData(
          "api-key"          -> settings.apiKey,
          "request_username" -> requester
        ).toEntity
      )
    )
  }

  override def changeAvatarUri(requester: String, organization: String): IO[List[String], Uri] =
    getChangeAvatarToken(requester, organization).map { token =>
      apiUri(_ / "accounts" / "user" / organization / "change-avatar" ++ Uri.Path.SingleSlash)
        .withQuery(Uri.Query("key" -> token.signedData))
    }
}
object AkkaSpongeAuthApi {

  def apply(
      settings: AkkaSpongeAuthSettings
  )(
      implicit system: ActorSystem,
      mat: Materializer
  ): UIO[AkkaSpongeAuthApi] =
    Ref.make(0L).map(counter => new AkkaSpongeAuthApi(settings, counter))

  case class AkkaSpongeAuthSettings(
      apiKey: String,
      apiUri: Uri,
      breakerMaxFailures: Int,
      breakerResetDur: FiniteDuration,
      breakerTimeoutDur: FiniteDuration
  ) extends AkkaClientApi.ClientSettings

  type LazyFuture[A] = () => Future[A]
}
