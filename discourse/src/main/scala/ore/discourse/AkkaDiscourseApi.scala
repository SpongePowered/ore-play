package ore.discourse

import scala.language.higherKinds

import scala.concurrent.duration.FiniteDuration

import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.external.AkkaClientApi

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.typesafe.scalalogging
import io.circe._

class AkkaDiscourseApi[F[_]] private (
    settings: AkkaDiscourseSettings,
    counter: Ref[F, Long]
)(
    implicit system: ActorSystem,
    mat: Materializer,
    F: Concurrent[F]
) extends AkkaClientApi[F, cats.Id]("Discourse", counter, settings)
    with DiscourseApi[F] {

  protected val Logger = scalalogging.Logger("DiscourseApi")

  private def startParams(poster: Option[String]) = Seq(
    "api_key"      -> settings.apiKey,
    "api_username" -> poster.getOrElse(settings.adminUser)
  )

  private def apiQuery(poster: Option[String]) =
    Uri.Query("api_key" -> settings.apiKey, "api_username" -> poster.getOrElse(settings.adminUser))

  protected def gatherJsonErrors[A: Decoder](json: Json): Either[String, A] = {
    val success = json.hcursor.downField("success")
    if (success.succeeded && !success.as[Boolean].getOrElse(false))
      Left(json.hcursor.get[String]("message").getOrElse("No error message found"))
    else
      json.as[A].leftMap(_.show)
  }

  override def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): EitherT[F, String, DiscoursePost] = {
    val base = startParams(Some(poster)) ++ Seq(
      "title" -> title,
      "raw"   -> content
    )

    val withCat = categoryId.fold(base)(i => base :+ ("category" -> i.toString))

    makeUnmarshallRequestEither[DiscoursePost](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "posts.json"),
        entity = FormData(withCat: _*).toEntity
      )
    )
  }

  override def createPost(poster: String, topicId: Int, content: String): EitherT[F, String, DiscoursePost] = {
    val params = startParams(Some(poster)) ++ Seq(
      "topic_id" -> topicId.toString,
      "raw"      -> content
    )

    makeUnmarshallRequestEither[DiscoursePost](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "posts.json"),
        entity = FormData(params: _*).toEntity
      )
    )
  }

  override def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): EitherT[F, String, Unit] = {
    if (title.isEmpty && categoryId.isEmpty) EitherT.rightT[F, String](())
    else {
      val base = startParams(Some(poster)) :+ ("topic_id" -> topicId.toString)

      val withTitle = title.fold(base)(t => base :+ ("title"                      -> t))
      val withCat   = categoryId.fold(withTitle)(c => withTitle :+ ("category_id" -> c.toString))

      makeUnmarshallRequestEither[Json](
        HttpRequest(
          HttpMethods.PUT,
          apiUri(_ / "t" / "-" / s"$topicId.json"),
          entity = FormData(withCat: _*).toEntity
        )
      ).void
    }
  }

  override def updatePost(poster: String, postId: Int, content: String): EitherT[F, String, Unit] = {
    val params = startParams(Some(poster)) :+ ("post[raw]" -> content)

    makeUnmarshallRequestEither[Json](
      HttpRequest(
        HttpMethods.PUT,
        apiUri(_ / "posts" / s"$postId.json"),
        entity = FormData(params: _*).toEntity
      )
    ).void
  }

  override def deleteTopic(poster: String, topicId: Int): EitherT[F, String, Unit] =
    EitherT
      .liftF(
        makeRequest(
          HttpRequest(
            HttpMethods.DELETE,
            apiUri(_ / "t" / s"$topicId.json").withQuery(apiQuery(Some(poster)))
          )
        )
      )
      .flatMap(gatherStatusErrors)
      .semiflatMap(resp => F.delay(resp.discardEntityBytes()))
      .void

  override def isAvailable: F[Boolean] = breaker.isClosed.pure
}
object AkkaDiscourseApi {
  def apply[F[_]: Concurrent](
      settings: AkkaDiscourseSettings
  )(implicit system: ActorSystem, mat: Materializer): F[AkkaDiscourseApi[F]] =
    Ref.of[F, Long](0L).map(counter => new AkkaDiscourseApi(settings, counter))

  case class AkkaDiscourseSettings(
      apiKey: String,
      adminUser: String,
      apiUri: Uri,
      breakerMaxFailures: Int,
      breakerResetDur: FiniteDuration,
      breakerTimeoutDur: FiniteDuration,
  ) extends AkkaClientApi.ClientSettings
}
