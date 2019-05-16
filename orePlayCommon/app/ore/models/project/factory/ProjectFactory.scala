package ore.models.project.factory

import java.nio.file.Files._
import javax.inject.Inject

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
import ore.models.user.{Notification, User}
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}
import ore.permission.role.Role
import ore.models.project.io._
import ore.util.StringUtils
import ore.util.StringUtils._
import ore.{OreConfig, OreEnv}
import util.syntax._

import akka.actor.ActorSystem
import cats.data.{EitherT, NonEmptyList}
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.google.common.base.Preconditions._

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit def service: ModelService[IO]
  implicit def projects: ProjectBase = ProjectBase.fromService

  def fileManager: ProjectFiles = this.projects.fileManager
  def cacheApi: SyncCacheApi
  def actorSystem: ActorSystem
  val dependencyVersionRegex: Regex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit def config: OreConfig
  implicit def forums: OreDiscourseApi
  implicit def env: OreEnv

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
            case Right(v) => v.exists
            case Left(_)  => IO.pure(false)
          }
          res <- version match {
            case Right(_) if modelExists && this.config.ore.projects.fileValidate =>
              IO.pure(Left("error.version.duplicate"))
            case Right(v) => v.cache.as(Right(v))
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
    * @param plugin First version file
    * @return PendingProject instance
    */
  def startProject(plugin: PluginFileWithData): PendingProject = {
    val metaData = plugin.data
    val owner    = plugin.user
    val name     = metaData.name.getOrElse("name not found")

    // Start a new pending project
    val pendingProject = PendingProject(
      pluginId = metaData.id.get,
      ownerName = owner.name,
      ownerId = owner.id,
      name = name,
      slug = slugify(name),
      visibility = Visibility.New,
      file = plugin,
      channelName = this.config.defaultChannelName,
      pendingVersion = null, // scalafix:ok
      cacheApi = this.cacheApi
    )
    //TODO: Remove cyclic dependency between PendingProject and PendingVersion
    pendingProject.pendingVersion = PendingProject.createPendingVersion(this, pendingProject)
    pendingProject
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
    * Returns the PendingProject of the specified owner and name, if any.
    *
    * @param owner Project owner
    * @param slug  Project slug
    * @return PendingProject if present, None otherwise
    */
  def getPendingProject(owner: String, slug: String): Option[PendingProject] =
    this.cacheApi.get[PendingProject](owner + '/' + slug)

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
    * Creates a new Project from the specified PendingProject
    *
    * @param pending PendingProject
    * @return New Project
    * @throws         IllegalArgumentException if the project already exists
    */
  def createProject(pending: PendingProject)(implicit cs: ContextShift[IO]): IO[Model[Project]] = {
    import cats.instances.vector._

    for {
      t <- (
        this.projects.exists(pending.ownerName, pending.name),
        this.projects.isNamespaceAvailable(pending.ownerName, pending.slug)
      ).parTupled
      (exists, available) = t
      _                   = checkArgument(!exists, "project already exists", "")
      _                   = checkArgument(available, "slug not available", "")
      _                   = checkArgument(this.config.isValidProjectName(pending.name), "invalid name", "")
      // Create the project and it's settings
      newProject <- service.insert(pending.asProject)
      _          <- service.insert(pending.settings.copy(projectId = newProject.id))
      _ <- {
        // Invite members
        val dossier   = newProject.memberships
        val ownerId   = newProject.ownerId
        val projectId = newProject.id

        val addRole = dossier.addRole(newProject)(
          ownerId,
          ProjectUserRole(ownerId, projectId, Role.ProjectOwner, isAccepted = true)
        )
        val addOtherRoles = pending.roles.toVector.parTraverse { role =>
          dossier.addRole(newProject)(role.userId, role.copy(projectId = projectId)) *>
            service.insert(
              Notification(
                userId = role.userId,
                originId = Some(ownerId),
                notificationType = NotificationType.ProjectInvite,
                messageArgs = NonEmptyList.of("notification.project.invite", role.role.title, newProject.name)
              )
            )
        }

        addRole *> addOtherRoles
      }
      withTopicId <- this.forums.createProjectTopic(newProject)
    } yield withTopicId
  }

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
  ): IO[(Model[Version], Model[Channel], Seq[Model[VersionTag]])] = {

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
      withTopicId <- if (project.topicId.isDefined && pending.createForumPost)
        this.forums.createVersionPost(project, version)
      else IO.pure(version)
    } yield (withTopicId, channel, tags)
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

class OreProjectFactory @Inject()(
    override val service: ModelService[IO],
    override val config: OreConfig,
    override val forums: OreDiscourseApi,
    override val cacheApi: SyncCacheApi,
    override val actorSystem: ActorSystem,
    override val env: OreEnv
) extends ProjectFactory
