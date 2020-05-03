package ore.models.project.factory

import scala.util.matching.Regex

import play.api.cache.SyncCacheApi
import play.api.i18n.Messages

import db.impl.access.ProjectBase
import ore.data.user.notification.NotificationType
import ore.data.{Color, Platform}
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{DbRef, Model, ModelService}
import ore.member.MembershipDossier
import ore.models.{Job, JobInfo}
import ore.models.project._
import ore.models.project.io._
import ore.models.user.role.ProjectUserRole
import ore.models.user.{Notification, User}
import ore.permission.role.Role
import ore.util.StringUtils._
import ore.util.{OreMDC, StringUtils}
import ore.{OreConfig, OreEnv}
import util.FileIO
import util.syntax._

import cats.data.NonEmptyList
import cats.syntax.all._
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit protected def service: ModelService[UIO]
  implicit protected def projects: ProjectBase[UIO]

  type ParTask[+A] = zio.interop.ParIO[Any, Throwable, A]
  type ParUIO[+A]  = zio.interop.ParIO[Any, Nothing, A]
  type RIO[-R, +A] = ZIO[R, Nothing, A]

  protected def fileIO: FileIO[ZIO[Blocking, Nothing, *]]
  protected def fileManager: ProjectFiles[ZIO[Blocking, Nothing, *]]
  protected def cacheApi: SyncCacheApi
  protected val dependencyVersionRegex: Regex = """^[0-9a-zA-Z.,\[\]()-]+$""".r

  implicit protected def config: OreConfig
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
  ): ZIO[Blocking, String, PluginFileWithData] = {
    val pluginFileName = uploadData.pluginFileName

    // file extension constraints
    if (!pluginFileName.endsWith(".zip") && !pluginFileName.endsWith(".jar"))
      ZIO.fail("error.plugin.fileExtension")
    // check user's public key validity
    else {
      // move uploaded files to temporary directory while the project creation
      // process continues
      val tmpDir = this.env.tmp.resolve(owner.name)
      val createDirs = ZIO.whenM(fileIO.notExists(tmpDir)) {
        fileIO.createDirectories(tmpDir)
      }

      val moveToNewPluginPath = fileIO.executeBlocking(
        uploadData.pluginFile.moveTo(tmpDir.resolve(pluginFileName), replace = true)
      )

      val loadData = createDirs *> moveToNewPluginPath.flatMap { newPluginPath =>
        // create and load a new PluginFile instance for further processing
        val plugin = new PluginFile(newPluginPath, owner)
        plugin.loadMeta[Task]
      }

      loadData.orDie.absolve
    }
  }

  def processSubsequentPluginUpload(uploadData: PluginUpload, uploader: Model[User], project: Model[Project])(
      implicit messages: Messages
  ): ZIO[Blocking, String, PendingVersion] =
    for {
      plugin <- processPluginUpload(uploadData, uploader)
        .ensure("error.version.invalidPluginId")(_.data.id.contains(project.pluginId))
        .ensure("error.version.illegalVersion")(!_.data.version.contains("recommended"))
      headChannel <- project
        .channels(ModelView.now(Channel))
        .one
        .getOrElseF(UIO.die(new IllegalStateException("No channel found for project")))
      version <- IO.fromEither(
        this.startVersion(
          plugin,
          project.pluginId,
          project.id,
          project.settings.forumSync,
          headChannel.name
        )
      )
      modelExists <- version.exists[Task].orDie
      res <- {
        if (modelExists && this.config.ore.projects.fileValidate) IO.fail("error.version.duplicate")
        else version.cache[Task].as(version).orDie
      }
    } yield res

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
  def createProject(
      owner: Model[User],
      template: ProjectTemplate
  ): IO[String, Model[Project]] = {
    val name = template.name
    val slug = slugify(name)
    val project = Project(
      pluginId = template.pluginId,
      ownerId = owner.id,
      ownerName = owner.name,
      name = name,
      slug = slug,
      category = template.category,
      description = template.description,
      visibility = Visibility.New
    )

    val channel: DbRef[Project] => Channel = Channel(_, config.defaultChannelName, config.defaultChannelColor)

    def cond[E](bool: Boolean, e: E) = if (bool) IO.succeed(()) else IO.fail(e)

    for {
      t <- (
        this.projects.withPluginId(template.pluginId).map(_.isDefined),
        this.projects.exists(owner.name, name),
        this.projects.isNamespaceAvailable(owner.name, slug)
      ).parTupled
      (existsId, existsName, available) = t
      _          <- cond(!existsName, "project with that name already exists")
      _          <- cond(!existsId, "project with that plugin id already exists")
      _          <- cond(available, "slug not available")
      _          <- cond(config.isValidProjectName(name), "invalid name")
      newProject <- service.insert(project)
      _          <- service.insert(channel(newProject.id.value))
      _ <- {
        MembershipDossier
          .projectHasMemberships[UIO]
          .addRole(newProject)(
            owner.id,
            ProjectUserRole(owner.id, newProject.id, Role.ProjectOwner, isAccepted = true)
          )
      }
    } yield newProject
  }

  /**
    * Starts the construction process of a [[Version]].
    *
    * @param plugin  Plugin file
    * @return PendingVersion instance
    */
  def startVersion(
      plugin: PluginFileWithData,
      pluginId: String,
      projectId: DbRef[Project],
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
          dependencies = metaData.dependencies.toList,
          description = metaData.description,
          projectId = projectId,
          fileSize = path.toFile.length,
          hash = plugin.md5,
          fileName = path.getFileName.toString,
          authorId = plugin.user.id,
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
    * @param project Project for version
    * @param version Name of version
    * @return PendingVersion, if present, None otherwise
    */
  def getPendingVersion(project: Model[Project], version: String): Option[PendingVersion] =
    this.cacheApi.get[PendingVersion](s"${project.id}/$version")

  /**
    * Creates a new release channel for the specified [[Project]].
    *
    * @param project Project to create channel for
    * @param name    Channel name
    * @param color   Channel color
    * @return New channel
    */
  def createChannel(project: Model[Project], name: String, color: Color): UIO[Model[Channel]] = {
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    for {
      limitReached <- service.runDBIO(
        (project.channels(ModelView.later(Channel)).size < config.ore.projects.maxChannels).result
      )
      _ = checkState(limitReached, "channel limit reached", "")
      channel <- service.insert(Channel(project.id, name, color))
    } yield channel
  }

  private def notifyWatchers(
      version: Model[Version],
      project: Model[Project]
  ): UIO[Unit] = {
    //TODO: Rewrite the entire operation to never have to leave the DB
    val notification = (userId: DbRef[User]) =>
      Notification(
        userId = userId,
        originId = Some(project.ownerId),
        notificationType = NotificationType.NewProjectVersion,
        messageArgs = NonEmptyList.of("notification.project.newVersion", project.name, version.name),
        action = Some(version.url(project))
      )

    val watchingUserIds =
      service.runDBIO(project.watchers.allQueryFromParent.filter(_.id =!= version.authorId).map(_.id).result)
    val notifications = watchingUserIds.map(_.map(notification))

    notifications.flatMap(service.bulkInsert(_).unit)
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
  ): ZIO[Blocking, Nothing, (Model[Project], Model[Version], Model[Channel], Seq[Model[VersionTag]])] = {

    for {
      // Create channel if not exists
      t <- (getOrCreateChannel(pending, project), pending.exists[Task].orDie).parTupled: ZIO[
        Blocking,
        Nothing,
        (Model[Channel], Boolean)
      ]
      (channel, exists) = t
      _ <- if (exists && this.config.ore.projects.fileValidate)
        UIO.die(new IllegalArgumentException("Version already exists."))
      else UIO.unit
      // Create version
      version <- service.insert(pending.asVersion(project.id, channel.id))
      tags    <- addTags(pending, version)
      // Notify watchers
      _ <- notifyWatchers(version, project)
      _ <- uploadPlugin(project, pending.plugin, version).orDieWith(s => new Exception(s))
      firstTimeUploadProject <- {
        if (project.visibility == Visibility.New) {
          val setVisibility = project
            .setVisibility(Visibility.Public, "First upload", version.authorId.getOrElse(project.ownerId))
            .map(_._1)

          val addForumJob = service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob)

          val initProject =
            if (project.topicId.isEmpty) addForumJob *> setVisibility
            else setVisibility

          initProject <* projects.refreshHomePage(MDCLogger)(OreMDC.NoMDC)
        } else {
          projects.refreshHomePage(MDCLogger)(OreMDC.NoMDC).as(project)
        }
      }
      _ <- if (pending.createForumPost) {
        service.insert(Job.UpdateDiscourseVersionPost.newJob(version.id).toJob).unit
      } else UIO.unit
    } yield (firstTimeUploadProject, version, channel, tags)
  }

  private def addTags(pendingVersion: PendingVersion, newVersion: Model[Version]): UIO[Seq[Model[VersionTag]]] =
    (
      pendingVersion.plugin.data.createTags(newVersion.id),
      addDependencyTags(newVersion)
    ).parMapN(_ ++ _)

  private def addDependencyTags(version: Model[Version]): UIO[Seq[Model[VersionTag]]] =
    Platform
      .createPlatformTags(
        version.id,
        // filter valid dependency versions
        version.dependencies.filter(_.version.forall(dependencyVersionRegex.pattern.matcher(_).matches()))
      )

  private def getOrCreateChannel(pending: PendingVersion, project: Model[Project]) =
    project
      .channels(ModelView.now(Channel))
      .find(equalsIgnoreCase(_.name, pending.channelName))
      .getOrElseF(createChannel(project, pending.channelName, pending.channelColor))

  private def uploadPlugin(
      project: Project,
      plugin: PluginFileWithData,
      version: Version
  ): ZIO[Blocking, String, Unit] = {
    val oldPath = plugin.path

    val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
    val newPath    = versionDir.resolve(oldPath.getFileName)

    val move: ZIO[Blocking, Nothing, Right[Nothing, Unit]] = {
      val createDirs = ZIO.whenM(fileIO.notExists(newPath.getParent)) {
        fileIO.createDirectories(newPath.getParent)
      }
      val movePath  = fileIO.move(oldPath, newPath)
      val deleteOld = fileIO.deleteIfExists(oldPath)

      createDirs *> movePath *> deleteOld.as(Right(()))
    }

    fileIO.exists(newPath).ifM(UIO.succeed(Left("error.plugin.fileName")), move).absolve
  }

}

class OreProjectFactory(
    val service: ModelService[UIO],
    val config: OreConfig,
    val cacheApi: SyncCacheApi,
    val env: OreEnv,
    val projects: ProjectBase[UIO],
    val fileManager: ProjectFiles[ZIO[Blocking, Nothing, *]],
    val fileIO: FileIO[ZIO[Blocking, Nothing, *]]
) extends ProjectFactory
