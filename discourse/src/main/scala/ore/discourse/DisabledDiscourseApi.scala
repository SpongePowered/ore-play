package ore.discourse

import zio.{IO, UIO, ZIO}

class DisabledDiscourseApi extends DiscourseApi {

  private def notEnabled[A]: IO[DiscourseError, A] = ZIO.fail(DiscourseError.NotAvailable)

  override def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): IO[DiscourseError, DiscoursePost] = notEnabled

  override def createPost(poster: String, topicId: Int, content: String): IO[DiscourseError, DiscoursePost] =
    notEnabled

  override def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): IO[DiscourseError, Unit] = notEnabled

  override def updatePost(poster: String, postId: Int, content: String): IO[DiscourseError, Unit] = notEnabled

  override def deleteTopic(poster: String, topicId: Int): IO[DiscourseError, Unit] = notEnabled

  override def isAvailable: UIO[Boolean] = ZIO.succeed(false)
}
