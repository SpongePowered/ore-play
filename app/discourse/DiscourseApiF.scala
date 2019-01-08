package discourse

import scala.language.higherKinds

import java.nio.file.Path

import scala.concurrent.Future

import play.api.http.Status

import cats.data.{EitherT, OptionT}
import cats.effect.syntax.all._
import cats.effect.{Concurrent, IO, Timer}
import cats.syntax.all._
import org.spongepowered.play.discourse.DiscourseApi
import org.spongepowered.play.discourse.model.{DiscoursePost, DiscourseUser}

abstract class DiscourseApiF[F[_]: Concurrent: Timer] extends DiscourseApi {

  private def futureToF[A](future: => Future[A]) = IO.fromFuture(IO(future)).to[F]

  private def futureToEitherT[A, B](future: Future[Either[A, B]]) = EitherT(futureToF(future))

  private def emptyIsSuccess[A](xs: List[A]): Either[List[A], Unit] = xs match {
    case Nil => Right(())
    case err => Left(err)
  }

  /**
    * Returns true if a Discourse user exists with the given username.
    *
    * @param username Username to find
    * @return True if user exists
    */
  def userExistsF(username: String): F[Boolean] = futureToF(userExists(username))

  /**
    * Attempts to retrieve the user with the specified username from the
    * forums.
    *
    * @param username Username to find
    * @return         User data or none
    */
  def fetchUserF(username: String): OptionT[F, DiscourseUser] = OptionT(futureToF(fetchUser(username)))

  /**
    * Creates a new user on the Discourse instance.
    *
    * @param name     User's full name
    * @param username User's username
    * @param email    User's email
    * @param password User's password
    * @param active   True if user is active
    * @return         Either a list of errors or a new User ID
    */
  def createUserF(
      name: String,
      username: String,
      email: String,
      password: String,
      active: Boolean = true
  ): EitherT[F, List[String], Int] = futureToEitherT(createUser(name, username, email, password, active))

  /**
    * Adds a group to the specified user.
    *
    * @param userId   User ID
    * @param groupId  ID of group to add
    */
  def addUserGroupF(userId: Int, groupId: Int): EitherT[F, List[String], Unit] =
    EitherT(futureToF(addUserGroup(userId, groupId)).map(emptyIsSuccess))

  /**
    * Sets the avatar for the specified user.
    *
    * @param username   User username
    * @param avatarUrl  Avatar URL
    * @return           List of errors if any
    */
  def setAvatarF(username: String, avatarUrl: String): EitherT[F, List[String], Unit] =
    EitherT(futureToF(setAvatar(username, avatarUrl)).map(emptyIsSuccess))

  /**
    * Sets the avatar for the specified user.
    *
    * @param username User username
    * @param path     Avatar file
    * @return         List of errors if any
    */
  def setAvatarF(username: String, fileName: String, path: Path): EitherT[F, List[String], Unit] =
    EitherT(futureToF(setAvatar(username, fileName, path)).map(emptyIsSuccess))

  // Posts

  /**
    * Creates a new topic as the specified poster.
    *
    * @param poster       Poster username
    * @param title        Topic title
    * @param content      Topic raw content
    * @param categoryId   Optional category id
    * @return             New topic or list of errors
    */
  def createTopicF(
      poster: String,
      title: String,
      content: String,
      categoryId: Option[Int]
  ): EitherT[F, List[String], DiscoursePost] = futureToEitherT(createTopic(poster, title, content, categoryId))

  /**
    * Creates a new post as the specified user.
    *
    * @param username User to post as
    * @param topicId  Topic ID
    * @param content  Raw content
    * @return         New post or list of errors
    */
  def createPostF(username: String, topicId: Int, content: String): EitherT[F, List[String], DiscoursePost] =
    futureToEitherT(createPost(username, topicId, content))

  /**
    * Updates a topic as the specified user.
    *
    * @param username   Username to update as
    * @param topicId    Topic ID
    * @param title      Optional new topic title
    * @param categoryId Optional new category ID
    * @return           List of errors
    */
  def updateTopicF(
      username: String,
      topicId: Int,
      title: Option[String],
      categoryId: Option[Int]
  ): EitherT[F, List[String], Unit] =
    EitherT(futureToF(updateTopic(username, topicId, title, categoryId)).map(emptyIsSuccess))

  /**
    * Updates a post as the specified user.
    *
    * @param username User to update as
    * @param postId   Post ID
    * @param content  Raw content
    * @return         List of errors
    */
  def updatePostF(username: String, postId: Int, content: String): EitherT[F, List[String], Unit] =
    EitherT(futureToF(updatePost(username, postId, content)).map(emptyIsSuccess))

  /**
    * Deletes the specified topic.
    *
    * @param username User to delete as
    * @param topicId  Topic ID
    */
  def deleteTopicF(username: String, topicId: Int): F[Unit] = futureToF(deleteTopic(username, topicId)).flatMap {
    case true  => ().pure
    case false => (new Exception("Delete topic failed"): Throwable).raiseError[F, Unit]
  }

  // Utils

  /**
    * Returns true if the Discourse instance is available.
    *
    * @return True if available
    */
  def isAvailableF: F[Boolean] = {
    import scala.concurrent.duration._
    val finiteTimeout = if (timeout.isFinite()) timeout.toMillis.millis else 1.second
    futureToF(this.ws.url(this.url).get()).map(_.status == Status.OK).timeoutTo(finiteTimeout, false.pure)
  }
}
