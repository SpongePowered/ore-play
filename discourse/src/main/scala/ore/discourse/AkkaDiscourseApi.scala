package ore.discourse

import scala.concurrent.duration._

import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.external.AkkaClientApi

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.syntax.all._
import com.typesafe.scalalogging
import com.typesafe.scalalogging.Logger
import io.circe._
import zio.{IO, Ref, UIO, ZIO}

class AkkaDiscourseApi private (
    settings: AkkaDiscourseSettings,
    counter: Ref[Long]
)(
    implicit system: ActorSystem,
    mat: Materializer
) extends AkkaClientApi[cats.Id, DiscourseError]("Discourse", counter, settings)
    with DiscourseApi {

  protected val Logger: Logger = scalalogging.Logger("DiscourseApi")

  private def authHeaders(poster: Option[String]) = Seq(
    `Api-Key`(settings.apiKey),
    `Api-Username`(poster.getOrElse(settings.adminUser))
  )

  override def createStatusError(statusCode: StatusCode, message: Option[String]): DiscourseError = statusCode match {
    case StatusCodes.TooManyRequests =>
      message match {
        case Some(jsonStr) =>
          parser.parse(jsonStr).flatMap(_.hcursor.downField("extras").get[Int]("wait_seconds")) match {
            case Right(value) =>
              DiscourseError.RatelimitError(value.seconds)

            case Left(ParsingFailure(errMessage, e)) =>
              Logger.warn(s"Failed to parse JSON in 429 from Discourse. Error: $errMessage To parse: $jsonStr", e)

              DiscourseError.RatelimitError(12.hours)
            case Left(DecodingFailure(errMessage, ops)) =>
              Logger.warn(
                s"Failed to get wait time in 429 from Discourse. Error: $errMessage Path: ${CursorOp.opsToPath(ops)} Json: $jsonStr"
              )

              DiscourseError.RatelimitError(12.hours)
          }

        case None =>
          Logger.warn("Received 429 from Discourse with no body. Assuming wait time of 12 hours")
          DiscourseError.RatelimitError(12.hours)
      }

    case _ => DiscourseError.StatusError(statusCode, message)
  }

  protected def gatherJsonErrors[A: Decoder](json: Json): Either[DiscourseError, A] = {
    val cursor = json.hcursor

    if (cursor.downField("errors").succeeded || cursor.downField("error_type").succeeded) {
      //If we can't find an error field here we just grab a sensible default
      Left(
        DiscourseError.UnknownError(
          cursor.get[Seq[String]]("errors").getOrElse(Nil),
          cursor.get[String]("error_type").getOrElse("unknown"),
          cursor.get[Map[String, Json]]("extras").fold(_ => Map.empty, _.map(t => t._1 -> t._2.noSpaces))
        )
      )
    } else {
      json.as[A].leftMap(_ => DiscourseError.UnknownError(Seq("err.show"), "unknown", Map.empty))
    }
  }

  override def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): IO[DiscourseError, DiscoursePost] = {
    val base = Seq(
      "title" -> title,
      "raw"   -> content
    )

    val withCat = categoryId.fold(base)(i => base :+ ("category" -> i.toString))

    runRequest[DiscoursePost](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "posts.json"),
        headers = authHeaders(Some(poster)),
        entity = FormData(withCat: _*).toEntity
      )
    )
  }

  override def createPost(poster: String, topicId: Int, content: String): IO[DiscourseError, DiscoursePost] = {
    val params = Seq(
      "topic_id" -> topicId.toString,
      "raw"      -> content
    )

    runRequest[DiscoursePost](
      HttpRequest(
        HttpMethods.POST,
        apiUri(_ / "posts.json"),
        headers = authHeaders(Some(poster)),
        entity = FormData(params: _*).toEntity
      )
    )
  }

  override def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): IO[DiscourseError, Unit] = {
    if (title.isEmpty && categoryId.isEmpty) ZIO.unit
    else {
      val base = Seq("topic_id" -> topicId.toString)

      val withTitle = title.fold(base)(t => base :+ ("title"                      -> t))
      val withCat   = categoryId.fold(withTitle)(c => withTitle :+ ("category_id" -> c.toString))

      runRequest[Json](
        HttpRequest(
          HttpMethods.PUT,
          apiUri(_ / "t" / "-" / s"$topicId.json"),
          headers = authHeaders(Some(poster)),
          entity = FormData(withCat: _*).toEntity
        )
      ).unit
    }
  }

  override def updatePost(poster: String, postId: Int, content: String): IO[DiscourseError, Unit] = {
    runRequest[Json](
      HttpRequest(
        HttpMethods.PUT,
        apiUri(_ / "posts" / s"$postId.json"),
        headers = authHeaders(Some(poster)),
        entity = FormData("post[raw]" -> content).toEntity
      )
    ).unit
  }

  override def deleteTopic(poster: String, topicId: Int): IO[DiscourseError, Unit] =
    makeRequest(
      HttpRequest(
        HttpMethods.DELETE,
        apiUri(_ / "t" / s"$topicId.json"),
        headers = authHeaders(Some(poster))
      )
    ).flatMap(gatherStatusErrors)
      .flatMap(resp => ZIO.effectTotal(resp.discardEntityBytes()))
      .unit

  override def isAvailable: UIO[Boolean] = ZIO.effectTotal(breaker.isClosed)
}
object AkkaDiscourseApi {
  def apply(
      settings: AkkaDiscourseSettings
  )(implicit system: ActorSystem, mat: Materializer): UIO[AkkaDiscourseApi] =
    Ref.make(0L).map(counter => new AkkaDiscourseApi(settings, counter))

  case class AkkaDiscourseSettings(
      apiKey: String,
      adminUser: String,
      apiUri: Uri,
      breakerMaxFailures: Int,
      breakerResetDur: FiniteDuration,
      breakerTimeoutDur: FiniteDuration
  ) extends AkkaClientApi.ClientSettings
}
