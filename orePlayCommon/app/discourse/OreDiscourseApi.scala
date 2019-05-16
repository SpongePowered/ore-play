package discourse

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import ore.models.project.{Project, Version}
import ore.models.user.User
import ore.OreConfig
import ore.db.{Model, ModelService}
import ore.discourse.{DiscourseApi, DiscoursePost}
import ore.util.StringUtils._
import util.syntax._

import akka.actor.Scheduler
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.google.common.base.Preconditions.checkArgument
import com.typesafe.scalalogging

/**
  * An implementation of [[DiscourseApi]] suited to Ore's needs.
  *
  * Note: It is very important that the implementor of this trait is a
  * singleton, otherwise countless threads will be spawned from this object's
  * [[RecoveryTask]].
  */
abstract class OreDiscourseApi(val api: DiscourseApi[IO])(implicit cs: ContextShift[IO]) {

  def isEnabled: Boolean

  /** The category where project topics are posted to */
  def categoryDefault: Int

  /** The category where deleted project topics are moved to */
  def categoryDeleted: Int

  /** Path to project topic template */
  def topicTemplatePath: Path

  /** Path to version release template */
  def versionReleasePostTemplatePath: Path

  /** Rate at which to retry failed attempts */
  def retryRate: FiniteDuration

  /** Scheduler for maintaining synchronization when requests fail */
  def scheduler: Scheduler

  /** The base URL for this instance */
  def baseUrl: String

  /** An admin account to fall back to if no user is specified as poster */
  def admin: String

  private val MDCLogger           = scalalogging.Logger.takingImplicit[DiscourseMDC]("Discourse")
  protected[discourse] val Logger = scalalogging.Logger(MDCLogger.underlying)

  /**
    * Initializes and starts this API instance.
    */
  def start(implicit ec: ExecutionContext, service: ModelService[IO], config: OreConfig): Unit = {
    if (!this.isEnabled) {
      Logger.info("Discourse API initialized in disabled mode.")
    } else {
      new RecoveryTask(this.scheduler, this.retryRate, this).start()
      Logger.info("Discourse API initialized.")
    }
  }

  /**
    * Creates a new topic for the specified [[Project]].
    *
    * @param project Project to create topic for.
    * @return        True if successful
    */
  def createProjectTopic(
      project: Model[Project]
  )(implicit service: ModelService[IO], config: OreConfig): IO[Model[Project]] = {
    if (!this.isEnabled)
      IO.pure(project)
    else {
      val title = Templates.projectTitle(project)

      implicit val mdc: DiscourseMDC = DiscourseMDC(project.ownerName, None, title)

      val createTopicProgram = (content: String) =>
        api.createTopic(
          poster = project.ownerName,
          title = title,
          content = content,
          categoryId = Some(categoryDefault)
      )

      def sanityCheck(check: Boolean, msg: => String) = if (!check) IO.raiseError(new Exception(msg)) else IO.unit

      val res = for {
        content <- EitherT.right[(String, String)](Templates.projectTopic(project))
        topic   <- createTopicProgram(content).leftMap((_, content))
        // Topic created!
        // Catch some unexpected cases (should never happen)
        _ <- EitherT.right[(String, String)](sanityCheck(topic.isTopic, "project post isn't topic?"))
        _ <- EitherT.right[(String, String)](
          sanityCheck(topic.username == project.ownerName, "project post user isn't owner?")
        )
        _ = MDCLogger.debug(s"""|New project topic:
                                |Project: ${project.url}
                                |Topic ID: ${topic.topicId}
                                |Post ID: ${topic.postId}""".stripMargin)
        project <- EitherT.right[(String, String)](
          service.update(project)(_.copy(topicId = Some(topic.topicId), postId = Some(topic.postId)))
        )
      } yield project

      res
        .leftSemiflatMap {
          case (error, _) =>
            // Request went through but Discourse responded with errors
            // Don't schedule a retry because this will just keep happening
            val message =
              s"""|Request to create topic for project '${project.url}' might have been successful but there were errors along the way:
                  |Errors: $error""".stripMargin
            MDCLogger.warn(message)
            project.logError(message).as(project)
        }
        .merge
        .onError {
          case e =>
            IO(MDCLogger.warn(s"Could not create project topic for project ${project.url}. Rescheduling...", e))
        }
    }
  }

  /**
    * Updates a [[Project]]'s forum topic with the appropriate content.
    *
    * @param project  Project to update topic for
    * @return         True if successful
    */
  def updateProjectTopic(
      project: Model[Project]
  )(implicit service: ModelService[IO], config: OreConfig): IO[Boolean] = {
    if (!this.isEnabled)
      IO.pure(true)
    else {
      checkArgument(project.topicId.isDefined, "undefined topic id", "")
      checkArgument(project.postId.isDefined, "undefined post id", "")

      val topicId   = project.topicId
      val postId    = project.postId
      val title     = Templates.projectTitle(project)
      val ownerName = project.ownerName

      implicit val mdc: DiscourseMDC = DiscourseMDC(ownerName, topicId, title)

      def logErrorsAs(error: String, as: Boolean): IO[Boolean] = {
        val message =
          s"""|Request to update project topic was successful but Discourse responded with errors:
              |Project: ${project.url}
              |Topic ID: $topicId
              |Title: $title
              |Errors: $error""".stripMargin
        MDCLogger.warn(message)
        project.logError(message).as(as)
      }

      val updateTopicProgram =
        api.updateTopic(poster = ownerName, topicId = topicId.get, title = Some(title), categoryId = None)

      val updatePostProgram = (content: String) =>
        api.updatePost(poster = ownerName, postId = postId.get, content = content)

      val res = for {
        // Set flag so that if we are interrupted we will remember to do it later
        _       <- EitherT.right[Boolean](service.update(project)(_.copy(isTopicDirty = true)))
        content <- EitherT.right[Boolean](Templates.projectTopic(project))
        _       <- updateTopicProgram.leftSemiflatMap(logErrorsAs(_, as = false))
        _       <- updatePostProgram(content).leftSemiflatMap(logErrorsAs(_, as = false))
        _ = MDCLogger.debug(s"Project topic updated for ${project.url}.")
        _ <- EitherT.right[Boolean](service.update(project)(_.copy(isTopicDirty = false)))
      } yield true

      res.merge
    }
  }

  /**
    * Posts a new reply to a [[Project]]'s forum topic.
    *
    * @param project  Project to post to
    * @param user     User who is posting
    * @param content  Post content
    * @return         List of errors Discourse returns
    */
  def postDiscussionReply(project: Project, user: User, content: String): EitherT[IO, String, DiscoursePost] = {
    if (!this.isEnabled) {
      Logger.warn("Tried to post discussion with API disabled?") // Shouldn't be reachable
      EitherT.leftT[IO, DiscoursePost]("Tried to post discussion with API disabled")
    } else {
      checkArgument(project.topicId.isDefined, "undefined topic id", "")
      EitherT(
        api
          .createPost(poster = user.name, topicId = project.topicId.get, content = content)
          .value
          .orElse(IO.pure(Left("Could not connect to forums, please try again later.")))
      )
    }
  }

  /**
    * Posts a new "Version release" to a [[Project]]'s forum topic.
    *
    * @param project Project to post release to
    * @param version Version of project
    * @return
    */
  def createVersionPost(project: Model[Project], version: Model[Version])(
      implicit service: ModelService[IO],
      cs: ContextShift[IO]
  ): IO[Model[Version]] = {
    if (!this.isEnabled)
      IO.pure(version)
    else {
      checkArgument(version.projectId == project.id.value, "invalid version project pair", "")
      EitherT
        .liftF(project.user)
        .flatMap { user =>
          postDiscussionReply(
            project,
            user,
            content = Templates.versionRelease(project, version, version.description)
          )
        }
        .leftSemiflatMap(error => project.logError(error).as(version))
        .semiflatMap(post => service.update(version)(_.copy(postId = Some(post.postId))))
        .merge
    }
  }

  def updateVersionPost(project: Model[Project], version: Model[Version])(
      implicit service: ModelService[IO]
  ): IO[Boolean] = {
    if (!this.isEnabled)
      IO.pure(true)
    else {
      checkArgument(project.topicId.isDefined, "undefined topic id", "")
      checkArgument(version.postId.isDefined, "undefined post id", "")

      val topicId   = project.topicId
      val postId    = version.postId
      val title     = Templates.projectTitle(project)
      val ownerName = project.ownerName

      implicit val mdc: DiscourseMDC = DiscourseMDC(ownerName, topicId, title)

      def logErrorsAs(error: String, as: Boolean): IO[Boolean] = {
        val message =
          s"""|Request to update project topic was successful but Discourse responded with errors:
              |Project: ${project.url}
              |Topic ID: $topicId
              |Title: $title
              |Errors: $error""".stripMargin
        MDCLogger.warn(message)
        project.logError(message).as(as)
      }

      val updatePostProgram = (content: String) =>
        api.updatePost(poster = ownerName, postId = postId.get, content = content)

      val res = for {
        // Set flag so that if we are interrupted we will remember to do it later
        _ <- EitherT.right[Boolean](service.update(version)(_.copy(isPostDirty = true)))
        content = Templates.versionRelease(project, version, version.description)
        _ <- updatePostProgram(content).leftSemiflatMap(logErrorsAs(_, as = false))
        _ = MDCLogger.debug(s"Version post updated for ${project.url}.")
        _ <- EitherT.right[Boolean](service.update(version)(_.copy(isPostDirty = false)))
      } yield true

      res.merge
    }
  }

  def changeTopicVisibility(project: Project, isVisible: Boolean): IO[Unit] = {
    if (!this.isEnabled)
      IO.pure(())
    else {
      checkArgument(project.topicId.isDefined, "undefined topic id", "")

      api
        .updateTopic(admin, project.topicId.get, None, Some(if (isVisible) categoryDefault else categoryDeleted))
        .fold(
          errors =>
            IO.raiseError(
              new Exception(s"Couldn't hide topic for project: ${project.url}. Message: " + errors.mkString(" | "))
          ),
          _ => IO.unit
        )
        .flatten
    }
  }

  /**
    * Delete's a [[Project]]'s forum topic.
    *
    * @param project  Project to delete topic for
    * @return         True if deleted
    */
  def deleteProjectTopic(project: Model[Project])(implicit service: ModelService[IO]): IO[Model[Project]] = {
    if (!this.isEnabled)
      IO.pure(project)
    else {
      checkArgument(project.topicId.isDefined, "undefined topic id", "")

      def logFailure(e: String) =
        IO(Logger.warn(s"Couldn't delete topic for project: ${project.url} because $e. Rescheduling..."))

      val deleteForums =
        api.deleteTopic(admin, project.topicId.get).onError { case e => EitherT.liftF(logFailure(e)) }.value
      deleteForums *> service.update(project)(_.copy(topicId = None, postId = None))
    }
  }

  /**
    * Discourse content templates.
    */
  object Templates {

    /** Creates a new title for a project topic. */
    def projectTitle(project: Project): String = project.name + project.description.fold("")(d => s" - $d")

    /** Generates the content for a project topic. */
    def projectTopic(
        project: Model[Project]
    )(implicit config: OreConfig, service: ModelService[IO]): IO[String] = project.homePage.map { page =>
      readAndFormatFile(
        topicTemplatePath,
        project.name,
        baseUrl + '/' + project.url,
        page.contents
      )
    }

    /** Generates the content for a version release post. */
    def versionRelease(project: Project, version: Version, content: Option[String]): String = {
      implicit val p: Project = project
      readAndFormatFile(
        versionReleasePostTemplatePath,
        project.name,
        baseUrl + '/' + project.url,
        baseUrl + '/' + version.url,
        content.getOrElse("*No description given.*")
      )
    }

  }

}
