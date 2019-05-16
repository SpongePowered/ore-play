package ore.discourse

import scala.language.higherKinds

import cats.data.EitherT

trait DiscourseApi[F[_]] {

  /**
    * Creates a new topic as the specified poster.
    *
    * @param poster       Poster username
    * @param title        Topic title
    * @param content      Topic raw content
    * @param categoryId   Optional category id
    * @return             New topic or list of errors
    */
  def createTopic(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): EitherT[F, String, DiscoursePost]

  /**
    * Creates a new post as the specified user.
    *
    * @param poster User to post as
    * @param topicId  Topic ID
    * @param content  Raw content
    * @return         New post or list of errors
    */
  def createPost(poster: String, topicId: Int, content: String): EitherT[F, String, DiscoursePost]

  /**
    * Updates a topic as the specified user.
    *
    * @param poster     Username to update as
    * @param topicId    Topic ID
    * @param title      Optional new topic title
    * @param categoryId Optional new category ID
    * @return           List of errors
    */
  def updateTopic(
      poster: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): EitherT[F, String, Unit]

  /**
    * Updates a post as the specified user.
    *
    * @param poster   User to update as
    * @param postId   Post ID
    * @param content  Raw content
    * @return         List of errors
    */
  def updatePost(poster: String, postId: Int, content: String): EitherT[F, String, Unit]

  /**
    * Deletes the specified topic.
    *
    * @param poster   User to delete as
    * @param topicId  Topic ID
    */
  def deleteTopic(poster: String, topicId: Int): EitherT[F, String, Unit]

  // Utils

  /**
    * Returns true if the Discourse instance is available.
    *
    * @return True if available
    */
  def isAvailable: F[Boolean]

}
