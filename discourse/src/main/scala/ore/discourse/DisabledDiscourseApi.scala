package ore.discourse

import scala.language.higherKinds

import cats.Applicative
import cats.syntax.all._

class DisabledDiscourseApi[F[_]](implicit F: Applicative[F]) extends DiscourseApi[F] {

  private def notEnabled[A]: F[Either[DiscourseError, A]] = F.pure(Left(DiscourseError.NotAvailable))

  override def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): F[Either[DiscourseError, DiscoursePost]] = notEnabled

  override def createPost(poster: String, topicId: Int, content: String): F[Either[DiscourseError, DiscoursePost]] =
    notEnabled

  override def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): F[Either[DiscourseError, Unit]] = notEnabled

  override def updatePost(poster: String, postId: Int, content: String): F[Either[DiscourseError, Unit]] = notEnabled

  override def deleteTopic(poster: String, topicId: Int): F[Either[DiscourseError, Unit]] = notEnabled

  override def isAvailable: F[Boolean] = false.pure
}
