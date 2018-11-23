package discourse

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import db.ModelService
import models.project.{Project, Version}
import models.user.User
import ore.OreConfig
import util.StringUtils._
import util.syntax._

import akka.actor.Scheduler
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.fasterxml.jackson.core.JsonParseException
import com.google.common.base.Preconditions.checkArgument
import org.slf4j.MDC
import org.spongepowered.play.discourse.DiscourseApi

/**
  * An implementation of [[DiscourseApi]] suited to Ore's needs.
  *
  * Note: It is very important that the implementor of this trait is a
  * singleton, otherwise countless threads will be spawned from this object's
  * [[RecoveryTask]].
  */
trait OreDiscourseApi extends DiscourseApi {

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

  /**
    * Initializes and starts this API instance.
    */
  def start(implicit ec: ExecutionContext, service: ModelService, config: OreConfig): Unit = {
    if (!this.isEnabled) {
      Logger.info("Discourse API initialized in disabled mode.")
      return
    }
    new RecoveryTask(this.scheduler, this.retryRate, this).start()
    Logger.info("Discourse API initialized.")
  }

  /**
    * Creates a new topic for the specified [[Project]].
    *
    * @param project Project to create topic for.
    * @return        True if successful
    */
  def createProjectTopic(
      project: Project
  )(implicit service: ModelService, config: OreConfig): IO[Project] = {
    if (!this.isEnabled)
      return IO.pure(project)
    checkArgument(project.isDefined, "undefined project", "")
    val title = Templates.projectTitle(project)

    val createTopicF = (content: String) =>
      IO.fromFuture(
        IO(
          createTopic(poster = project.ownerName, title = title, content = content, categoryId = Some(categoryDefault))
        )
    )

    def sanityCheck(check: Boolean, msg: => String) = if (!check) IO.raiseError(new Exception(msg)) else IO.unit

    val res = for {
      content <- EitherT.right[(List[String], String)](Templates.projectTopic(project))
      topic   <- EitherT(createTopicF(content)).leftMap((_, content))
      // Topic created!
      // Catch some unexpected cases (should never happen)
      _ <- EitherT.right[(List[String], String)](sanityCheck(topic.isTopic, "project post isn't topic?"))
      _ <- EitherT.right[(List[String], String)](
        sanityCheck(topic.username == project.ownerName, "project post user isn't owner?")
      )
      _ = Logger.debug(s"""|New project topic:
                           |Project: ${project.url}
                           |Topic ID: ${topic.topicId}
                           |Post ID: ${topic.postId}""".stripMargin)
      project <- EitherT.right[(List[String], String)](
        service.update(project.copy(topicId = Some(topic.topicId), postId = Some(topic.postId)))
      )
    } yield project

    res
      .leftSemiflatMap {
        case (errors, content) =>
          // Request went through but Discourse responded with errors
          // Don't schedule a retry because this will just keep happening
          val message =
            s"""|Request to create project topic was successful but Discourse responded with errors:
                |Project: ${project.url}
                |Title: $title
                |Content: $content
                |Errors: ${errors.mkString(", ")}""".stripMargin
          Logger.warn(message)
          project.logger.flatMap(_.err(message)).as(project)
      }
      .merge
      .onError {
        case e =>
          IO(Logger.warn(s"Could not create project topic for project ${project.url}. Rescheduling...", e))
      }
  }

  /**
    * Updates a [[Project]]'s forum topic with the appropriate content.
    *
    * @param project  Project to update topic for
    * @return         True if successful
    */
  def updateProjectTopic(
      project: Project
  )(implicit service: ModelService, config: OreConfig): IO[Boolean] = {
    if (!this.isEnabled)
      return IO.pure(true)
    checkArgument(project.isDefined, "undefined project", "")
    checkArgument(project.topicId.isDefined, "undefined topic id", "")
    checkArgument(project.postId.isDefined, "undefined post id", "")

    val topicId   = project.topicId
    val postId    = project.postId
    val title     = Templates.projectTitle(project)
    val ownerName = project.ownerName

    def logErrorsAs(errors: List[String], as: Boolean): IO[Boolean] = {
      val message =
        s"""|Request to update project topic was successful but Discourse responded with errors:
            |Project: ${project.url}
            |Topic ID: $topicId
            |Title: $title
            |Errors: ${errors.toString}""".stripMargin
      Logger.warn(message)
      project.logger.flatMap(_.err(message)).as(as)
    }

    val updateTopicF = IO
      .fromFuture(
        IO(updateTopic(username = ownerName, topicId = topicId.get, title = Some(title), categoryId = None))
      )
      .map(xs => Either.cond(xs.isEmpty, (), xs))

    val updatePostF = (content: String) =>
      IO.fromFuture(IO(updatePost(username = ownerName, postId = postId.get, content = content)))
        .map(xs => Either.cond(xs.isEmpty, (), xs))

    val res = for {
      // Set flag so that if we are interrupted we will remember to do it later
      _       <- EitherT.right[Boolean](service.update(project.copy(isTopicDirty = true)))
      content <- EitherT.right[Boolean](Templates.projectTopic(project))
      _       <- EitherT(updateTopicF).leftSemiflatMap(logErrorsAs(_, as = false))
      _       <- EitherT(updatePostF(content)).leftSemiflatMap(logErrorsAs(_, as = false))
      _ = Logger.debug(s"Project topic updated for ${project.url}.")
      _ <- EitherT.right[Boolean](service.update(project.copy(isTopicDirty = false)))
    } yield true

    res.merge.onError {
      case e =>
        IO {
          MDC.put("username", ownerName)
          MDC.put("topicId", topicId.get.toString)
          MDC.put("title", title)
          e match {
            case runtimeException: RuntimeException =>
              if (runtimeException.getCause.isInstanceOf[JsonParseException]) {
                MDC.put("jsonException", runtimeException.getCause.getMessage)
              }
            case _ =>
          }
        }
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
  def postDiscussionReply(project: Project, user: User, content: String): IO[List[String]] = {
    if (!this.isEnabled) {
      Logger.warn("Tried to post discussion with API disabled?") // Shouldn't be reachable
      return IO.pure(Nil)
    }
    checkArgument(project.topicId.isDefined, "undefined topic id", "")
    // It's OK if Discourse responds with errors here, we will just show them to the user
    IO.fromFuture(IO(createPost(username = user.name, topicId = project.topicId.get, content = content)))
      .map(_.left.toOption.getOrElse(List.empty))
      .orElse(IO.pure(List("Could not connect to forums, please try again later.")))
  }

  /**
    * Posts a new "Version release" to a [[Project]]'s forum topic.
    *
    * @param project Project to post release to
    * @param version Version of project
    * @return
    */
  def postVersionRelease(project: Project, version: Version, content: Option[String])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[List[String]] = {
    import cats.instances.list._
    if (!this.isEnabled)
      return IO.pure(Nil)
    checkArgument(project.isDefined, "undefined project", "")
    checkArgument(version.isDefined, "undefined version", "")
    checkArgument(version.projectId == project.id.value, "invalid version project pair", "")
    project.owner.user.flatMap { user =>
      postDiscussionReply(project, user, content = Templates.versionRelease(project, version, content)).flatMap {
        case Nil    => IO.pure(Nil)
        case errors => project.logger.flatMap(logger => errors.parTraverse(logger.err)).as(errors)
      }
    }
  }

  def changeTopicVisibility(project: Project, isVisible: Boolean): IO[Boolean] = {
    if (!this.isEnabled)
      return IO.pure(true)

    checkArgument(project.isDefined, "undefined project", "")
    checkArgument(project.topicId.isDefined, "undefined topic id", "")

    IO.fromFuture(
        IO(updateTopic(admin, project.topicId.get, None, Some(if (isVisible) categoryDefault else categoryDeleted)))
      )
      .map {
        case Nil =>
          Logger.debug(s"Successfully updated topic category for project: ${project.url}.")
          true
        case list =>
          Logger.warn(s"Couldn't hide topic for project: ${project.url}. Message: " + list.mkString(" | "))
          false
      }
  }

  /**
    * Delete's a [[Project]]'s forum topic.
    *
    * @param project  Project to delete topic for
    * @return         True if deleted
    */
  def deleteProjectTopic(project: Project)(implicit service: ModelService): IO[Boolean] = {
    if (!this.isEnabled)
      return IO.pure(true)
    checkArgument(project.isDefined, "undefined project", "")
    checkArgument(project.topicId.isDefined, "undefined topic id", "")

    def logFailure(): Unit = Logger.warn(s"Couldn't delete topic for project: ${project.url}. Rescheduling...")

    IO.fromFuture(IO(deleteTopic(admin, project.topicId.get)))
      .flatMap {
        case false =>
          logFailure()
          IO.pure(false)
        case true =>
          Logger.debug(s"Successfully deleted project topic for: ${project.url}.")
          service.update(project.copy(topicId = None, postId = None)).as(true)
      }
      .orElse {
        logFailure()
        IO.pure(false)
      }
  }

  /**
    * Returns a future result of the amount of users on Discourse that are in
    * this list.
    *
    * @param users  Users to check
    * @return       Amount on discourse
    */
  def countUsers(users: List[String])(implicit cs: ContextShift[IO]): IO[Int] = {
    import cats.instances.list._
    if (!this.isEnabled)
      IO.pure(0)
    else
      users.parTraverse(u => IO.fromFuture(IO(userExists(u))).orElse(IO.pure(false))).map(_.count(_ == true))
  }

  /**
    * Discourse content templates.
    */
  object Templates {

    /** Creates a new title for a project topic. */
    def projectTitle(project: Project): String = project.name + project.description.fold("")(d => s" - $d")

    /** Generates the content for a project topic. */
    def projectTopic(
        project: Project
    )(implicit config: OreConfig, service: ModelService): IO[String] = project.homePage.map { page =>
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
