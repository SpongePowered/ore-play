package ore.models.project.factory

import java.nio.file.Files._
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.matching.Regex

import play.api.cache.SyncCacheApi
import play.api.i18n.Messages

import db.impl.access.ProjectBase
import ore.db.impl.OrePostgresDriver.api._
import discourse.OreDiscourseApi
import ore.data.user.notification.NotificationType
import ore.data.{Color, Platform}
import ore.models.project._
import ore.models.user.role.ProjectUserRole
import ore.models.user.User
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}
import ore.permission.role.Role
import ore.models.project.io._
import ore.util.{OreMDC, StringUtils}
import ore.util.StringUtils._
import ore.{OreConfig, OreEnv}
import util.syntax._

import akka.actor.ActorSystem
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit protected def service: ModelService[IO]
  implicit protected def projects: ProjectBase[IO]

  protected def fileManager: ProjectFiles
  protected def cacheApi: SyncCacheApi
  protected def actorSystem: ActorSystem
  protected val dependencyVersionRegex: Regex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit protected def config: OreConfig
  implicit protected def forums: OreDiscourseApi[IO]
  implicit protected def env: OreEnv

  private val Logger    = scalalogging.Logger("Projects")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  /**
    * Processes incoming [[PluginUpload]] data, verifies it, and loads a new
    * [[PluginFile]] for further processing.
    *
    * @param uploadData Upload data of request
    * @param owner      Upload owner
    * @return Loaded PluginFile
    */
  def processPluginUpload(uploadData: PluginUpload, owner: Model[User])(
      implicit messages: Messages
  ): EitherT[IO, String, PluginFileWithData] = {
    val pluginFileName = uploadData.pluginFileName

    // file extension constraints
    if (!pluginFileName.endsWith(".zip") && !pluginFileName.endsWith(".jar"))
      EitherT.leftT("error.plugin.fileExtension")
    // check user's public key validity
    else {
      // move uploaded files to temporary directory while the project creation
      // process continues
      val tmpDir = this.env.tmp.resolve(owner.name)
      if (notExists(tmpDir))
        createDirectories(tmpDir)

      val newPluginPath = uploadData.pluginFile.moveFileTo(tmpDir.resolve(pluginFileName), replace = true)

      // create and load a new PluginFile instance for further processing
      val plugin = new PluginFile(newPluginPath, owner)
      plugin.loadMeta
    }
  }

  def processSubsequentPluginUpload(uploadData: PluginUpload, owner: Model[User], project: Model[Project])(
      implicit messages: Messages,
      cs: ContextShift[IO]
  ): EitherT[IO, String, PendingVersion] =
    this
      .processPluginUpload(uploadData, owner)
      .ensure("error.version.invalidPluginId")(_.data.id.contains(project.pluginId))
      .ensure("error.version.illegalVersion")(!_.data.version.contains("recommended"))
      .flatMapF { plugin =>
        for {
          t <- (
            project
              .channels(ModelView.now(Channel))
              .one
              .getOrElseF(IO.raiseError(new IllegalStateException("No channel found for project"))),
            project.settings
          ).parTupled
          (headChannel, settings) = t
          version = this.startVersion(
            plugin,
            project.pluginId,
            Some(project.id),
            project.url,
            settings.forumSync,
            headChannel.name
          )
          modelExists <- version match {
            case Right(v) => v.exists[IO]
            case Left(_)  => IO.pure(false)
          }
          res <- version match {
            case Right(_) if modelExists && this.config.ore.projects.fileValidate =>
              IO.pure(Left("error.version.duplicate"))
            case Right(v) => v.cache[IO].as(Right(v))
            case Left(m)  => IO.pure(Left(m))
          }
        } yield res
      }

  /**
    * Returns the error ID to display to the User, if any, if they cannot
    * upload files.
    *
    * @return Upload error if any
    */
  def getUploadError(user: User): Option[String] =
    Seq(
      user.isLocked -> "error.user.locked"
    ).find(_._1).map(_._2)

  /**
    * Starts the construction process of a [[Project]].
    *
    * @param owner The owner of the project
    * @param template The values to use for the new project
    *
    * @return Project and ProjectSettings instance
    */
  def createProject(owner: Model[User], template: ProjectTemplate)(
      implicit cs: ContextShift[IO]
  ): EitherT[IO, String, (Project, ProjectSettings)] = {
    val name = template.name
    val slug = slugify(name)
    val project = Project(
      pluginId = template.pluginId,
      ownerName = owner.name,
      ownerId = owner.id,
      name = name,
      slug = slug,
      category = template.category,
      description = template.description,
      visibility = Visibility.New,
    )

    val projectSettings: DbRef[Project] => ProjectSettings = ProjectSettings(_)

    val channel: DbRef[Project] => Channel = Channel(_, config.defaultChannelName, config.defaultChannelColor)

    for {
      t <- EitherT.liftF(
        (
          this.projects.withPluginId(template.pluginId).isDefined,
          this.projects.exists(owner.name, name),
          this.projects.isNamespaceAvailable(owner.name, slug)
        ).parTupled
      )
      (existsId, existsName, available) = t
      _           <- EitherT.cond[IO].apply(!existsName, (), "project with that name already exists")
      _           <- EitherT.cond[IO].apply(!existsId, (), "project with that plugin id already exists")
      _           <- EitherT.cond[IO].apply(available, (), "slug not available")
      _           <- EitherT.cond[IO].apply(config.isValidProjectName(name), (), "invalid name")
      newProject  <- EitherT.right[String](service.insert(project))
      newSettings <- EitherT.right[String](service.insert(projectSettings(newProject.id.value)))
      _           <- EitherT.right[String](service.insert(channel(newProject.id.value)))
      _ <- EitherT.right[String](
        newProject.memberships.addRole(newProject)(
          owner.id,
          ProjectUserRole(owner.id, newProject.id, Role.ProjectOwner, isAccepted = true)
        )
      )
    } yield (newProject, newSettings)
  }

  /**
    * Starts the construction process of a [[Version]].
    *
    * @param plugin  Plugin file
    * @param project Parent project
    * @return PendingVersion instance
    */
  def startVersion(
      plugin: PluginFileWithData,
      pluginId: String,
      projectId: Option[DbRef[Project]],
      projectUrl: String,
      forumSync: Boolean,
      channelName: String
  ): Either[String, PendingVersion] = {
    val metaData = plugin.data
    if (!metaData.id.contains(pluginId))
      Left("error.plugin.invalidPluginId")
    else if (metaData.version.isEmpty)
      Left("error.plugin.noVersion")
    else {
      // Create new pending version
      val path = plugin.path

      Right(
        PendingVersion(
          versionString = StringUtils.slugify(metaData.version.get),
          dependencyIds = metaData.dependencies.map(d => d.pluginId + ":" + d.version).toList,
          description = metaData.description,
          projectId = projectId,
          fileSize = path.toFile.length,
          hash = plugin.md5,
          fileName = path.getFileName.toString,
          authorId = plugin.user.id,
          projectUrl = projectUrl,
          channelName = channelName,
          channelColor = this.config.defaultChannelColor,
          plugin = plugin,
          createForumPost = forumSync,
          cacheApi = cacheApi
        )
      )
    }
  }

  /**
    * Returns the pending version for the specified owner, name, channel, and
    * version string.
    *
    * @param owner   Name of owner
    * @param slug    Project slug
    * @param version Name of version
    * @return PendingVersion, if present, None otherwise
    */
  def getPendingVersion(owner: String, slug: String, version: String): Option[PendingVersion] =
    this.cacheApi.get[PendingVersion](owner + '/' + slug + '/' + version)

  /**
    * Creates a new release channel for the specified [[Project]].
    *
    * @param project Project to create channel for
    * @param name    Channel name
    * @param color   Channel color
    * @return New channel
    */
  def createChannel(project: Model[Project], name: String, color: Color): IO[Model[Channel]] = {
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    for {
      limitReached <- service.runDBIO(
        (project.channels(ModelView.later(Channel)).size < config.ore.projects.maxChannels).result
      )
      _ = checkState(limitReached, "channel limit reached", "")
      channel <- service.insert(Channel(project.id, name, color))
    } yield channel
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending PendingVersion
    * @return New version
    */
  def createVersion(
      project: Model[Project],
      pending: PendingVersion
  )(
      implicit ec: ExecutionContext,
      cs: ContextShift[IO]
  ): IO[(Model[Project], Model[Version], Model[Channel], Seq[Model[VersionTag]])] = {

    for {
      // Create channel if not exists
      t <- (getOrCreateChannel(pending, project), pending.exists).parTupled
      (channel, exists) = t
      _ <- if (exists && this.config.ore.projects.fileValidate)
        IO.raiseError(new IllegalArgumentException("Version already exists."))
      else IO.unit
      // Create version
      version <- service.insert(pending.asVersion(project.id, channel.id))
      tags    <- addTags(pending, version)
      // Notify watchers
      _ = this.actorSystem.scheduler.scheduleOnce(Duration.Zero, NotifyWatchersTask(version, project))
      _ <- uploadPlugin(project, pending.plugin, version).fold(e => IO.raiseError(new Exception(e)), IO.pure)
      firstTimeUploadProject <- {
        if (project.visibility == Visibility.New) {
          val setVisibility = (project: Model[Project]) => {
            project.setVisibility(Visibility.Public, "First upload", version.authorId).map(_._1)
          }
          val initProject =
            if (project.topicId.isEmpty) this.forums.createProjectTopic(project).flatMap(setVisibility)
            else setVisibility(project)

          initProject <* projects.refreshHomePage(MDCLogger)(OreMDC.NoMDC)

        } else IO.pure(project)
      }
      withTopicId <- if (firstTimeUploadProject.topicId.isDefined && pending.createForumPost)
        this.forums.createVersionPost(firstTimeUploadProject, version)
      else IO.pure(version)
    } yield (firstTimeUploadProject, withTopicId, channel, tags)
  }

  private def addTags(pendingVersion: PendingVersion, newVersion: Model[Version])(
      implicit cs: ContextShift[IO]
  ): IO[Seq[Model[VersionTag]]] =
    (
      pendingVersion.plugin.data.createTags(newVersion.id),
      addDependencyTags(newVersion)
    ).parMapN(_ ++ _)

  private def addDependencyTags(version: Model[Version]): IO[Seq[Model[VersionTag]]] =
    Platform
      .createPlatformTags(
        version.id,
        // filter valid dependency versions
        version.dependencies.filter(d => dependencyVersionRegex.pattern.matcher(d.version).matches())
      )

  private def getOrCreateChannel(pending: PendingVersion, project: Model[Project]) =
    project
      .channels(ModelView.now(Channel))
      .find(equalsIgnoreCase(_.name, pending.channelName))
      .getOrElseF(createChannel(project, pending.channelName, pending.channelColor))

  private def uploadPlugin(project: Project, plugin: PluginFileWithData, version: Version): EitherT[IO, String, Unit] =
    EitherT(
      IO {
        val oldPath = plugin.path

        val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
        val newPath    = versionDir.resolve(oldPath.getFileName)

        if (exists(newPath))
          Left("error.plugin.fileName")
        else {
          if (!exists(newPath.getParent))
            createDirectories(newPath.getParent)

          move(oldPath, newPath)
          deleteIfExists(oldPath)
          Right(())
        }
      }
    )

}

@Singleton
class OreProjectFactory @Inject()(
    val service: ModelService[IO],
    val config: OreConfig,
    val forums: OreDiscourseApi[IO],
    val cacheApi: SyncCacheApi,
    val actorSystem: ActorSystem,
    val env: OreEnv,
    val projects: ProjectBase[IO],
    val fileManager: ProjectFiles
) extends ProjectFactory
