package ore.discourse

import scala.language.higherKinds

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.{Materializer, StreamTcpException}
import cats.data.EitherT
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Fiber, Timer}
import cats.syntax.all._
import cats.effect.syntax.all._
import com.typesafe.scalalogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._

class AkkaDiscourseApi[F[_]: Concurrent: Timer: ContextShift] private (
    settings: AkkaDiscourseSettings,
    isAvailableRef: Ref[F, Option[Either[Boolean, Fiber[F, Boolean]]]],
    counter: Ref[F, Long]
)(
    implicit system: ActorSystem,
    mat: Materializer
) extends DiscourseApi[F]
    with FailFastCirceSupport {

  private def nextCounter: F[Long] = counter.modify(c => (c + 1, c))

  private def startParams(poster: Option[String]) = Seq(
    "api_key"      -> settings.apiKey,
    "api_username" -> poster.getOrElse(settings.adminUser)
  )

  private val Logger = scalalogging.Logger("DiscourseApi")

  private def apiQuery(poster: Option[String]) =
    Uri.Query("api_key" -> settings.apiKey, "api_username" -> poster.getOrElse(settings.adminUser))

  private def F: Concurrent[F] = Concurrent[F]

  private def apiUri(f: Uri.Path => Uri.Path) = settings.apiUri.withPath(f(settings.apiUri.path))

  private def futureToF[A](future: => Future[A]) = {
    import system.dispatcher
    F.async[A] { callback =>
      future.onComplete(t => callback(t.toEither))
    }
  }

  private def debugF[A](before: => String, after: A => String, fa: F[A]): F[A] = {
    if (Logger.underlying.isDebugEnabled) {
      nextCounter.flatMap { c =>
        F.delay(Logger.debug(s"$c $before")) *> fa.flatTap(res => F.delay(Logger.debug(s"$c ${after(res)}")))
      }
    } else fa
  }

  private def makeRequestAlways(request: HttpRequest) =
    debugF[HttpResponse](
      s"Making request: $request",
      res => s"Request response: $res",
      futureToF(Http().singleRequest(request))
    )

  private def makeRequest(request: HttpRequest): EitherT[F, String, HttpResponse] = {
    val requestF = makeRequestAlways(request).onError {
      case _: StreamTcpException => resetIsAvailable
    }

    EitherT
      .right[String](isAvailable)
      .ifM(EitherT.right(requestF), EitherT.leftT("Discourse not available"))
  }

  private def unmarshallResponse[A](response: HttpResponse)(implicit um: Unmarshaller[HttpResponse, A]) =
    futureToF(Unmarshal(response).to[A])

  private def gatherStatusErrors(response: HttpResponse) = {
    if (response.status.isSuccess()) {
      EitherT.rightT[F, String](response)
    } else if (response.entity.isKnownEmpty()) {
      EitherT.left[HttpResponse](
        F.delay(response.entity.discardBytes()).as(s"Discourse request failed. Response code ${response.status}")
      )
    } else {
      EitherT
        .left[HttpResponse](unmarshallResponse[String](response))
        .leftMap(e => s"Discourse request failed. Response code ${response.status}: $e")
    }
  }

  private def gatherJsonErrors[A: Decoder](json: Json) = {
    val success = json.hcursor.downField("success")
    if (success.succeeded && !success.as[Boolean].getOrElse(false))
      Left(json.hcursor.get[String]("message").getOrElse("No error message found"))
    else
      json.as[A].leftMap(_.show)
  }

  private def makeUnmarshallRequestEither[A: Decoder](request: HttpRequest) =
    makeRequest(request)
      .flatMap(gatherStatusErrors)
      .semiflatMap(unmarshallResponse[Json])
      .subflatMap(gatherJsonErrors[A])

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
    makeRequest(
      HttpRequest(
        HttpMethods.DELETE,
        apiUri(_ / "t" / s"$topicId.json").withQuery(apiQuery(Some(poster)))
      )
    ).flatMap(gatherStatusErrors)
      .semiflatMap(resp => F.delay(resp.discardEntityBytes()))
      .void

  private val resetIsAvailable = for {
    runner <- isAvailableRef.getAndSet(None).map(_.flatMap(_.toOption))
    _      <- runner.fold(F.unit)(_.cancel)
  } yield ()

  override def isAvailable: F[Boolean] = {
    val checkIfAvailable =
      makeRequestAlways(HttpRequest(HttpMethods.HEAD, settings.apiUri))
        .flatMap(resp => F.delay(resp.discardEntityBytes()))
        .as(true)
        .recover {
          case _: StreamTcpException => false
        }

    //A fiber which invalidates the state after some time
    val invalidateState = Timer[F].sleep(settings.isAvailableReset).productR(resetIsAvailable).start

    isAvailableRef.access.flatMap {
      case (Some(value), _) =>
        //If there is already a value around, or it will be around soon, use that
        value.fold(F.pure, _.join)
      case (None, update) =>
        //A program which will run the availability check, set the result when it finishes, and schedule an invalidation
        val checkAndSet = checkIfAvailable.flatTap { result =>
          isAvailableRef.set(Some(Left(result))) *> invalidateState
        }

        //Starts the fiber, and tries to set the current fiber as the one we just started.
        //If setting the value succeeds, then we join the fiber. If it fails, we cancel the fiber, and recurse.
        checkAndSet.start.flatMap(fiber => update(Some(Right(fiber))).ifM(fiber.join, fiber.cancel *> isAvailable))
    }
  }
}
object AkkaDiscourseApi {
  def apply[F[_]: Concurrent: Timer: ContextShift](
      settings: AkkaDiscourseSettings
  )(implicit system: ActorSystem, mat: Materializer): F[AkkaDiscourseApi[F]] =
    for {
      counter     <- Ref.of[F, Long](0L)
      isAvailable <- Ref.of[F, Option[Either[Boolean, Fiber[F, Boolean]]]](None)
    } yield new AkkaDiscourseApi(settings, isAvailable, counter)

  case class AkkaDiscourseSettings(
      apiKey: String,
      adminUser: String,
      isAvailableReset: FiniteDuration,
      apiUri: Uri
  )
}
