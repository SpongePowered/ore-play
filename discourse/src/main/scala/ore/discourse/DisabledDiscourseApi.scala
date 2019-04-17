package ore.discourse

import scala.language.higherKinds

import cats.Applicative
import cats.data.EitherT
import cats.syntax.all._

class DisabledDiscourseApi[F[_]: Applicative] extends DiscourseApi[F] {

  private def notEnabled[A]: EitherT[F, String, A] = EitherT.leftT[F, A]("Discourse not enabled")

  override def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): EitherT[F, String, DiscoursePost] = notEnabled

  override def createPost(poster: String, topicId: Int, content: String): EitherT[F, String, DiscoursePost] = notEnabled

  override def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): EitherT[F, String, Unit] = notEnabled

  override def updatePost(poster: String, postId: Int, content: String): EitherT[F, String, Unit] = notEnabled

  override def deleteTopic(poster: String, topicId: Int): EitherT[F, String, Unit] = notEnabled

  override def isAvailable: F[Boolean] = false.pure
}
