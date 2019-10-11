package discourse

import scala.language.higherKinds

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import ore.OreConfig
import ore.db.{Model, ModelService}
import ore.discourse.{DiscourseApi, DiscourseError, DiscoursePost}
import ore.models.project.{Project, Version}
import ore.models.user.User
import ore.util.StringUtils.readAndFormatFile
import util.syntax._

import akka.actor.Scheduler
import cats.Parallel
import cats.data.EitherT
import cats.effect.Effect
import cats.syntax.all._
import com.typesafe.scalalogging

/**
  * A base implementation of [[OreDiscourseApi]] that uses [[DiscourseApi]].
  *
  * Note: It is very important that the implementor of this trait is a
  * singleton, otherwise countless threads will be spawned from this object's
  * [[RecoveryTask]].
  *
  * @param categoryDefault The category where project topics are posted to
  * @param categoryDeleted The category where deleted project topics are moved to
  * @param topicTemplatePath Path to project topic template
  * @param versionReleasePostTemplatePath Path to version release template
  * @param retryRate Rate at which to retry failed attempts
  * @param scheduler Scheduler for maintaining synchronization when requests fail
  * @param admin An admin account to fall back to if no user is specified as poster
  */
class OreDiscourseApiEnabled[F[_]](
    api: DiscourseApi[F],
    categoryDefault: Int,
    categoryDeleted: Int,
    topicTemplatePath: Path,
    versionReleasePostTemplatePath: Path,
    retryRate: FiniteDuration,
    scheduler: Scheduler,
    admin: String
)(
    implicit service: ModelService[F],
    config: OreConfig,
    F: Effect[F],
    par: Parallel[F]
) extends OreDiscourseApi[F] {

  private val MDCLogger           = scalalogging.Logger.takingImplicit[DiscourseMDC]("Discourse")
  protected[discourse] val Logger = scalalogging.Logger(MDCLogger.underlying)

  //TODO: At some point we should handle these errors better
  private def errToString(err: DiscourseError): String = err match {
    case DiscourseError.NotAvailable => "Could not connect to forums, please try again later."
    case _                           => err.toString
  }

  /**
    * Initializes and starts this API instance.
    */
  def start()(implicit ec: ExecutionContext): Unit = {
    new RecoveryTask(this.scheduler, this.retryRate, this).start()
    Logger.info("Discourse API initialized.")
  }

  /**
    * Creates a new topic for the specified [[Project]].
    *
    * @param project Project to create topic for.
    * @return        True if successful
    */
  def createProjectTopic(project: Model[Project]): F[Model[Project]] = {
    val title = Templates.projectTitle(project)

    implicit val mdc: DiscourseMDC = DiscourseMDC(project.ownerName, None, title)

    val createTopicProgram = (content: String) =>
      api.createTopic(
        poster = project.ownerName,
        title = title,
        content = content,
        categoryId = Some(categoryDefault)
      )

    def sanityCheck(check: Boolean, msg: => String) = if (!check) F.raiseError[Unit](new Exception(msg)) else F.unit

    val res = for {
      content <- EitherT.right[(String, String)](Templates.projectTopic(project))
      topic   <- EitherT(createTopicProgram(content)).leftMap(err => (errToString(err), content))
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
          F.pure(project)
      }
      .merge
      .onError {
        case e =>
          F.delay(MDCLogger.warn(s"Could not create project topic for project ${project.url}. Rescheduling...", e))
      }
  }

  /**
    * Updates a [[Project]]'s forum topic with the appropriate content.
    *
    * @param project  Project to update topic for
    * @return         True if successful
    */
  def updateProjectTopic(project: Model[Project]): F[Boolean] = {
    require(project.topicId.isDefined, "undefined topic id")
    require(project.postId.isDefined, "undefined post id")

    val topicId   = project.topicId
    val postId    = project.postId
    val title     = Templates.projectTitle(project)
    val ownerName = project.ownerName

    implicit val mdc: DiscourseMDC = DiscourseMDC(ownerName, topicId, title)

    def logErrorsAs(error: DiscourseError, as: Boolean): F[Boolean] = {
      val message =
        s"""|Request to update project topic was successful but Discourse responded with errors:
            |Project: ${project.url}
            |Topic ID: $topicId
            |Title: $title
            |Errors: $error""".stripMargin
      MDCLogger.warn(message)
      F.pure(as)
    }

    val updateTopicProgram =
      api.updateTopic(poster = ownerName, topicId = topicId.get, title = Some(title), categoryId = None)

    val updatePostProgram = (content: String) =>
      api.updatePost(poster = ownerName, postId = postId.get, content = content)

    val res = for {
      // Set flag so that if we are interrupted we will remember to do it later
      _       <- EitherT.right[Boolean](service.update(project)(_.copy(isTopicDirty = true)))
      content <- EitherT.right[Boolean](Templates.projectTopic(project))
      _       <- EitherT(updateTopicProgram).leftSemiflatMap(logErrorsAs(_, as = false))
      _       <- EitherT(updatePostProgram(content)).leftSemiflatMap(logErrorsAs(_, as = false))
      _ = MDCLogger.debug(s"Project topic updated for ${project.url}.")
      _ <- EitherT.right[Boolean](service.update(project)(_.copy(isTopicDirty = false)))
    } yield true

    res.merge
  }

  /**
    * Posts a new reply to a [[Project]]'s forum topic.
    *
    * @param project  Project to post to
    * @param user     User who is posting
    * @param content  Post content
    * @return         List of errors Discourse returns
    */
  def postDiscussionReply(project: Project, user: User, content: String): F[Either[String, DiscoursePost]] = {
    require(project.topicId.isDefined, "undefined topic id")
    api
      .createPost(poster = user.name, topicId = project.topicId.get, content = content)
      .map(_.leftMap(errToString))
      .orElse(F.pure(Left("Could not connect to forums, please try again later.")))
  }

  /**
    * Posts a new "Version release" to a [[Project]]'s forum topic.
    *
    * @param project Project to post release to
    * @param version Version of project
    * @return
    */
  def createVersionPost(project: Model[Project], version: Model[Version]): F[Model[Version]] = {
    require(version.projectId == project.id.value, "invalid version project pair")
    EitherT
      .liftF(project.user)
      .flatMapF { user =>
        postDiscussionReply(
          project,
          user,
          content = Templates.versionRelease(project, version, version.description)
        )
      }
      .leftSemiflatMap(_ => F.pure(version))
      .semiflatMap(post => service.update(version)(_.copy(postId = Some(post.postId))))
      .merge
  }

  def updateVersionPost(project: Model[Project], version: Model[Version]): F[Boolean] = {
    require(project.topicId.isDefined, "undefined topic id")
    require(version.postId.isDefined, "undefined post id")

    val topicId   = project.topicId
    val postId    = version.postId
    val title     = Templates.projectTitle(project)
    val ownerName = project.ownerName

    implicit val mdc: DiscourseMDC = DiscourseMDC(ownerName, topicId, title)

    def logErrorsAs(error: DiscourseError, as: Boolean): F[Boolean] = {
      val message =
        s"""|Request to update project topic was successful but Discourse responded with errors:
            |Project: ${project.url}
            |Topic ID: $topicId
            |Title: $title
            |Errors: $error""".stripMargin
      MDCLogger.warn(message)
      F.pure(as)
    }

    val updatePostProgram = (content: String) =>
      api.updatePost(poster = ownerName, postId = postId.get, content = content)

    val res = for {
      // Set flag so that if we are interrupted we will remember to do it later
      _ <- EitherT.right[Boolean](service.update(version)(_.copy(isPostDirty = true)))
      content = Templates.versionRelease(project, version, version.description)
      _ <- EitherT(updatePostProgram(content)).leftSemiflatMap(logErrorsAs(_, as = false))
      _ = MDCLogger.debug(s"Version post updated for ${project.url}.")
      _ <- EitherT.right[Boolean](service.update(version)(_.copy(isPostDirty = false)))
    } yield true

    res.merge
  }

  def changeTopicVisibility(project: Project, isVisible: Boolean): F[Unit] = {
    require(project.topicId.isDefined, "undefined topic id")

    EitherT(
      api.updateTopic(admin, project.topicId.get, None, Some(if (isVisible) categoryDefault else categoryDeleted))
    ).fold(
        errors =>
          F.raiseError[Unit](
            new Exception(s"Couldn't hide topic for project: ${project.url}. Message: $errors")
          ),
        _ => F.unit
      )
      .flatten
  }

  /**
    * Delete's a [[Project]]'s forum topic.
    *
    * @param project  Project to delete topic for
    * @return         True if deleted
    */
  def deleteProjectTopic(project: Model[Project]): F[Model[Project]] = {
    require(project.topicId.isDefined, "undefined topic id")

    def logFailure(e: DiscourseError) =
      F.delay(Logger.warn(s"Couldn't delete topic for project: ${project.url} because $e. Rescheduling..."))

    val deleteForums =
      EitherT(api.deleteTopic(admin, project.topicId.get)).onError { case e => EitherT.liftF(logFailure(e)) }.value
    deleteForums *> service.update(project)(_.copy(topicId = None, postId = None))
  }

  /**
    * Discourse content templates.
    */
  object Templates {

    /** Creates a new title for a project topic. */
    def projectTitle(project: Project): String = project.name + project.description.fold("")(d => s" - $d")

    /** Generates the content for a project topic. */
    def projectTopic(project: Model[Project]): F[String] = project.homePage.map { page =>
      readAndFormatFile(
        topicTemplatePath,
        project.name,
        config.app.baseUrl + '/' + project.url,
        page.contents
      )
    }

    /** Generates the content for a version release post. */
    def versionRelease(project: Project, version: Version, content: Option[String]): String = {
      readAndFormatFile(
        versionReleasePostTemplatePath,
        project.name,
        config.app.baseUrl + '/' + project.url,
        config.app.baseUrl + '/' + version.url(project),
        content.getOrElse("*No description given.*")
      )
    }

  }

  override def isAvailable: F[Boolean] = api.isAvailable
}
