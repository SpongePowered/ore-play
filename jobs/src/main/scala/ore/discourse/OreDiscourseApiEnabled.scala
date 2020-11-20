package ore.discourse

import java.text.MessageFormat

import ore.OreJobsConfig
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{Model, ModelService}
import ore.models.project.{Page, Project, Version, Visibility}
import ore.models.user.User
import ore.syntax._

import com.typesafe.scalalogging
import com.typesafe.scalalogging.Logger
import zio.{IO, UIO, ZIO}

/**
  * A base implementation of [[OreDiscourseApi]] that uses [[DiscourseApi]].
  *
  * @param categoryDefault The category where project topics are posted to
  * @param categoryDeleted The category where deleted project topics are moved to
  * @param topicTemplate Project topic template
  * @param versionReleasePostTemplate Version release template
  * @param admin An admin account to fall back to if no user is specified as poster
  */
class OreDiscourseApiEnabled(
    api: DiscourseApi,
    categoryDefault: Int,
    categoryDeleted: Int,
    topicTemplate: String,
    versionReleasePostTemplate: String,
    admin: String
)(
    implicit service: ModelService[UIO],
    config: OreJobsConfig
) extends OreDiscourseApi {

  private val MDCLogger                   = scalalogging.Logger.takingImplicit[DiscourseMDC]("Discourse")
  protected[discourse] val Logger: Logger = scalalogging.Logger(MDCLogger.underlying)

  private def homePage(project: Model[Project]): UIO[Model[Page]] =
    ModelView
      .now(Page)
      .find(p => p.projectId === project.id.value && p.name === config.ore.pages.home.name)
      .value
      .flatMap {
        case Some(value) => ZIO.succeed(value)
        case None        => ZIO.die(new Exception("No homepage found for project"))
      }

  /**
    * Creates a new topic for the specified [[Project]].
    *
    * @param project Project to create topic for.
    * @return        True if successful
    */
  def createProjectTopic(project: Model[Project]): IO[DiscourseError, Model[Project]] = {
    val title = Templates.projectTitle(project)

    implicit val mdc: DiscourseMDC = DiscourseMDC(project.ownerName, None, title)

    val createTopicProgram = (content: String) =>
      api.createTopic(
        poster = project.ownerName,
        title = title,
        content = content,
        categoryId = Some(categoryDefault)
      )

    def sanityCheck(check: Boolean, msg: => String) = if (!check) ZIO.die(new Exception(msg)) else ZIO.unit

    for {
      homePage <- homePage(project)
      content = Templates.projectTopic(project, homePage.contents)
      topic <- createTopicProgram(content)
      // Topic created!
      // Catch some unexpected cases (should never happen)
      _ <- sanityCheck(topic.isTopic, "project post isn't topic?")
      _ <- sanityCheck(topic.username == project.ownerName, "project post user isn't owner?")
      _ <- ZIO.effectTotal(
        MDCLogger.debug(s"""|New project topic:
                            |Project: ${project.url}
                            |Topic ID: ${topic.topicId}
                            |Post ID: ${topic.postId}""".stripMargin)
      )
      project <- service.update(project)(_.copy(topicId = Some(topic.topicId), postId = Some(topic.postId)))
    } yield project
  }

  def updateProjectTopic(project: Model[Project]): IO[DiscourseError, Unit] = {
    require(project.topicId.isDefined, "undefined topic id")
    require(project.postId.isDefined, "undefined post id")

    val topicId   = project.topicId
    val postId    = project.postId
    val title     = Templates.projectTitle(project)
    val ownerName = project.ownerName

    implicit val mdc: DiscourseMDC = DiscourseMDC(ownerName, topicId, title)

    val updateTopicProgram =
      api.updateTopic(
        poster = ownerName,
        topicId = topicId.get,
        title = Some(title),
        categoryId = Some(if (Visibility.isPublic(project.visibility)) categoryDefault else categoryDeleted)
      )

    val updatePostProgram = (content: String) =>
      api.updatePost(poster = ownerName, postId = postId.get, content = content)

    for {
      homePage <- homePage(project)
      content = Templates.projectTopic(project, homePage.contents)
      _ <- updateTopicProgram
      _ <- updatePostProgram(content)
      _ <- ZIO.effectTotal(MDCLogger.debug(s"Project topic updated for ${project.url}."))
    } yield ()
  }

  def postDiscussionReply(topicId: Int, poster: String, content: String): IO[DiscourseError, DiscoursePost] =
    api.createPost(poster = poster, topicId = topicId, content = content)

  def createVersionPost(project: Model[Project], version: Model[Version]): IO[DiscourseError, Model[Version]] = {
    ModelView
      .now(User)
      .get(project.userId)
      .value
      .someOrFailException
      .orDie
      .flatMap { user =>
        postDiscussionReply(
          project.topicId.get,
          user.name,
          content = Templates.versionRelease(project, version, version.description)
        )
      }
      .flatMap(post => service.update(version)(_.copy(postId = Some(post.postId))))
  }

  def updateVersionPost(project: Model[Project], version: Model[Version]): IO[DiscourseError, Unit] = {
    require(project.topicId.isDefined, "undefined topic id")
    require(version.postId.isDefined, "undefined post id")

    val postId    = version.postId
    val ownerName = project.ownerName
    val content   = Templates.versionRelease(project, version, version.description)

    api.updatePost(poster = ownerName, postId = postId.get, content = content)
  }

  def deleteTopic(topicId: Int): IO[DiscourseError, Unit] =
    api.deleteTopic(admin, topicId)

  /**
    * Discourse content templates.
    */
  object Templates {

    /** Creates a new title for a project topic. */
    def projectTitle(project: Project): String = project.name + project.description.fold("")(d => s" - $d")

    /** Generates the content for a project topic. */
    def projectTopic(project: Model[Project], content: String): String = MessageFormat.format(
      topicTemplate,
      project.name,
      s"${config.ore.baseUrl}/${project.url}",
      content
    )

    /** Generates the content for a version release post. */
    def versionRelease(project: Project, version: Version, content: Option[String]): String = {
      MessageFormat.format(
        versionReleasePostTemplate,
        project.name,
        s"${config.ore.baseUrl}/${project.url}",
        s"${config.ore.baseUrl}/${version.url(project)}",
        content.getOrElse("*No description given.*")
      )
    }

  }
}
