package discourse

import scala.language.higherKinds

import ore.db.Model
import ore.discourse.{DiscourseError, DiscoursePost}
import ore.models.project.{Project, Version}
import ore.models.user.User

import cats.tagless.autoFunctorK

/**
  * Manages forum threads and posts for Ore models.
  */
@autoFunctorK
trait OreDiscourseApi[+F[_]] {

  /**
    * Creates a new topic for the specified [[Project]].
    *
    * @param project Project to create topic for.
    * @return        True if successful
    */
  def createProjectTopic(project: Model[Project]): F[Model[Project]]

  /**
    * Updates a [[Project]]'s forum topic with the appropriate content.
    *
    * @param project  Project to update topic for
    * @return         True if successful
    */
  def updateProjectTopic(project: Model[Project]): F[Boolean]

  /**
    * Posts a new reply to a [[Project]]'s forum topic.
    *
    * @param project  Project to post to
    * @param user     User who is posting
    * @param content  Post content
    * @return         Error Discourse returns
    */
  def postDiscussionReply(project: Project, user: User, content: String): F[Either[String, DiscoursePost]]

  /**
    * Posts a new "Version release" to a [[Project]]'s forum topic.
    *
    * @param project Project to post release to
    * @param version Version of project
    * @return
    */
  def createVersionPost(project: Model[Project], version: Model[Version]): F[Model[Version]]

  /**
    * Updates a [[Version]]s forum post with the appropriate content.
    * @param project The owner of the version
    * @param version The version to update post for
    * @return True if successful
    */
  def updateVersionPost(project: Model[Project], version: Model[Version]): F[Boolean]

  def changeTopicVisibility(project: Project, isVisible: Boolean): F[Unit]

  /**
    * Delete's a [[Project]]'s forum topic.
    *
    * @param project  Project to delete topic for
    * @return         True if deleted
    */
  def deleteProjectTopic(project: Model[Project]): F[Model[Project]]

  /**
    * Returns true if the forum instance is available.
    *
    * @return True if available
    */
  def isAvailable: F[Boolean]
}
