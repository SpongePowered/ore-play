package ore.project.factory

import java.nio.file.Files._
import java.nio.file.StandardCopyOption

import akka.actor.ActorSystem
import com.google.common.base.Preconditions._
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.{ProjectBase, UserBase}
import db.impl.{ProjectMembersTable, ProjectRoleTable}
import discourse.OreDiscourseApi
import javax.inject.Inject
import models.project._
import models.user.role.ProjectRole
import models.user.{Notification, User}
import ore.Colors.Color
import ore.permission.role.RoleType
import ore.project.factory.TagAlias.ProjectTag
import ore.project.io._
import ore.project.{NotifyWatchersTask, ProjectMember}
import ore.user.MembershipDossier
import ore.user.notification.NotificationTypes
import ore.{OreConfig, OreEnv, Platforms}
import play.api.cache.SyncCacheApi
import play.api.i18n.Messages
import security.pgp.PGPVerifier
import util.StringUtils._
import util.functional.EitherT
import util.instances.future._
import util.syntax._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.matching.Regex

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit val service: ModelService
  implicit val users: UserBase = this.service.getModelBase(classOf[UserBase])
  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  val fileManager: ProjectFiles = this.projects.fileManager
  val cacheApi: SyncCacheApi
  val actorSystem: ActorSystem
  val pgp: PGPVerifier = new PGPVerifier
  val dependencyVersionRegex: Regex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit val config: OreConfig
  implicit val forums: OreDiscourseApi
  implicit val env: OreEnv = this.fileManager.env

  var isPgpEnabled: Boolean = this.config.security.get[Boolean]("requirePgp")

  /**
    * Processes incoming [[PluginUpload]] data, verifies it, and loads a new
    * [[PluginFile]] for further processing.
    *
    * @param uploadData Upload data of request
    * @param owner      Upload owner
    * @return Loaded PluginFile
    */
  def processPluginUpload(uploadData: PluginUpload, owner: User)(implicit messages: Messages): Either[String, PluginFile] = {
    val pluginFileName = uploadData.pluginFileName
    var signatureFileName = uploadData.signatureFileName

    // file extension constraints
    if (!pluginFileName.endsWith(".zip") && !pluginFileName.endsWith(".jar"))
      throw InvalidPluginFileException("error.plugin.fileExtension")
    if (!signatureFileName.endsWith(".sig") && !signatureFileName.endsWith(".asc"))
      throw InvalidPluginFileException("error.plugin.sig.fileExtension")

    // check user's public key validity
    if (owner.pgpPubKey.isEmpty)
      throw new IllegalArgumentException("error.plugin.noPubKey")
    if (!owner.isPgpPubKeyReady)
      throw new IllegalArgumentException("error.plugin.pubKey.cooldown")

    var pluginPath = uploadData.pluginFile.path
    var sigPath = uploadData.signatureFile.path

    // verify detached signature
    if (!this.pgp.verifyDetachedSignature(pluginPath, sigPath, owner.pgpPubKey.get))
      throw InvalidPluginFileException("error.plugin.sig.failed")

    // move uploaded files to temporary directory while the project creation
    // process continues
    val tmpDir = this.env.tmp.resolve(owner.username)
    if (notExists(tmpDir))
      createDirectories(tmpDir)
    val signatureFileExtension = signatureFileName.substring(signatureFileName.lastIndexOf("."))
    signatureFileName = pluginFileName + signatureFileExtension
    pluginPath = copy(pluginPath, tmpDir.resolve(pluginFileName), StandardCopyOption.REPLACE_EXISTING)
    sigPath = copy(sigPath, tmpDir.resolve(signatureFileName), StandardCopyOption.REPLACE_EXISTING)

    // create and load a new PluginFile instance for further processing
    val plugin = new PluginFile(pluginPath, sigPath, owner)
    val result = plugin.loadMeta()
    result match {
      case Right(_) => Right(plugin)
      case Left(errorMessage) => Left(errorMessage)
    }
  }

  def processSubsequentPluginUpload(uploadData: PluginUpload,
                                    owner: User,
                                    project: Project)(implicit ec: ExecutionContext, messages: Messages): EitherT[Future, String, PendingVersion] = {
    this.processPluginUpload(uploadData, owner) match {
      case Right(plugin) if !plugin.data.flatMap(_.id).contains(project.pluginId) =>
        EitherT.leftT("error.version.invalidPluginId")
      case Right(plugin) =>
        EitherT(
          for {
            (channels, settings) <- (project.channels.all, project.settings).parTupled
            version = this.startVersion(plugin, project, settings, channels.head.name)
            modelExists <- version match {
              case Right(v) => v.underlying.exists
              case Left(_) => Future.successful(false)
            }
          } yield {
            version match {
              case Right(v) => if (modelExists && this.config.projects.get[Boolean]("file-validate"))
                Left("error.version.duplicate")
              else {
                v.cache()
                Right(v)
              }
              case Left(m) => Left(m)
            }

          }
        )
      case Left(errorMessage) => EitherT.leftT[Future, PendingVersion](errorMessage)
    }

  }

  /**
    * Returns the error ID to display to the User, if any, if they cannot
    * upload files.
    *
    * @return Upload error if any
    */
  def getUploadError(user: User): Option[String] = {
    if (this.isPgpEnabled) {
      // Make sure user has a key
      if (user.pgpPubKey.isEmpty)
        return Some("error.pgp.noPubKey")
      // Make sure the user has waited long enough to use a key
      if (!user.isPgpPubKeyReady)
        return Some("error.pgp.keyChangeCooldown")
    }
    if (user.isLocked)
      return Some("error.user.locked")
    None
  }

  /**
    * Starts the construction process of a [[Project]].
    *
    * @param plugin First version file
    * @return PendingProject instance
    */
  def startProject(plugin: PluginFile): PendingProject = {
    val metaData = checkMeta(plugin)
    val owner = plugin.user

    // Start a new pending project
    val project = Project.Builder(this.service)
      .pluginId(metaData.id.get)
      .ownerName(owner.name)
      .ownerId(owner.id.value)
      .name(metaData.get[String]("name").getOrElse("name not found"))
      .visibility(VisibilityTypes.New)
      .build()

    val pendingProject = PendingProject(
      projects = this.projects,
      factory = this,
      underlying = project,
      file = plugin,
      config = this.config,
      channelName = this.config.getSuggestedNameForVersion(metaData.version.get),
      cacheApi = this.cacheApi)
    pendingProject
  }

  /**
    * Starts the construction process of a [[Version]].
    *
    * @param plugin  Plugin file
    * @param project Parent project
    * @return PendingVersion instance
    */
  def startVersion(plugin: PluginFile, project: Project, settings: ProjectSettings, channelName: String): Either[String, PendingVersion] = {
    val metaData = checkMeta(plugin)
    if (!metaData.id.contains(project.pluginId))
      return Left("error.plugin.invalidPluginId")

    // Create new pending version
    val path = plugin.path
    val version = Version.Builder(this.service)
      .versionString(metaData.version.get)
      .dependencyIds(metaData.dependencies.map(d => d.pluginId + ":" + d.version).toList)
      .description(metaData.get[String]("description").getOrElse(""))
      .projectId(project.id.unsafeToOption.getOrElse(-1)) // Version might be for an uncreated project
      .fileSize(path.toFile.length)
      .hash(plugin.md5)
      .fileName(path.getFileName.toString)
      .signatureFileName(plugin.signaturePath.getFileName.toString)
      .authorId(plugin.user.id.value)
      .build()

    Right(PendingVersion(
      projects = this.projects,
      factory = this,
      project = project,
      channelName = channelName,
      channelColor = this.config.defaultChannelColor,
      underlying = version,
      plugin = plugin,
      createForumPost = settings.forumSync,
      cacheApi = cacheApi
    ))
  }

  private def checkMeta(plugin: PluginFile): PluginFileData
  = plugin.data.getOrElse(throw new IllegalStateException("plugin metadata not loaded?"))

  /**
    * Returns the PendingProject of the specified owner and name, if any.
    *
    * @param owner Project owner
    * @param slug  Project slug
    * @return PendingProject if present, None otherwise
    */
  def getPendingProject(owner: String, slug: String): Option[PendingProject]
  = this.cacheApi.get[PendingProject](owner + '/' + slug)

  /**
    * Returns the pending version for the specified owner, name, channel, and
    * version string.
    *
    * @param owner   Name of owner
    * @param slug    Project slug
    * @param version Name of version
    * @return PendingVersion, if present, None otherwise
    */
  def getPendingVersion(owner: String, slug: String, version: String): Option[PendingVersion]
  = this.cacheApi.get[PendingVersion](owner + '/' + slug + '/' + version)

  /**
    * Creates a new Project from the specified PendingProject
    *
    * @param pending PendingProject
    * @return New Project
    * @throws         IllegalArgumentException if the project already exists
    */
  def createProject(pending: PendingProject)(implicit ec: ExecutionContext): Future[Project] = {
    val project = pending.underlying

    for {
      (exists, available) <- (
        this.projects.exists(project),
        this.projects.isNamespaceAvailable(project.ownerName, project.slug)
      ).parTupled
      _ = checkArgument(!exists, "project already exists", "")
      _ = checkArgument(available, "slug not available", "")
      _ = checkArgument(this.config.isValidProjectName(pending.underlying.name), "invalid name", "")
      // Create the project and it's settings
      newProject <- this.projects.add(pending.underlying)
    } yield {
      newProject.updateSettings(pending.settings)

      // Invite members
      val dossier: MembershipDossier {
        type MembersTable = ProjectMembersTable

        type MemberType = ProjectMember

        type RoleTable = ProjectRoleTable

        type ModelType = Project

        type RoleType = ProjectRole
      } = newProject.memberships
      val owner = newProject.owner
      val ownerId = owner.userId
      val projectId = newProject.id.value

      dossier.addRole(new ProjectRole(ownerId, RoleType.ProjectOwner, projectId, accepted = true, visible = true))
      pending.roles.map { role =>
        role.user.map { user =>
          dossier.addRole(role.copy(projectId = projectId))
          user.sendNotification(Notification(
            originId = ownerId,
            notificationType = NotificationTypes.ProjectInvite,
            messageArgs = List("notification.project.invite", role.roleType.title, project.name)
          ))
        }
      }

      this.forums.createProjectTopic(newProject)

      newProject
    }
  }

  /**
    * Creates a new release channel for the specified [[Project]].
    *
    * @param project Project to create channel for
    * @param name    Channel name
    * @param color   Channel color
    * @return New channel
    */
  def createChannel(project: Project, name: String, color: Color, nonReviewed: Boolean)(implicit ec: ExecutionContext): Future[Channel] = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    checkNotNull(name, "null name", "")
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    checkNotNull(color, "null color", "")
    for {
      channelCount <- project.channels.size
      _ = checkState(channelCount < this.config.projects.get[Int]("max-channels"), "channel limit reached", "")
      channel <- this.service.access[Channel](classOf[Channel]).add(new Channel(name, color, project.id.value))
    } yield channel
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending PendingVersion
    * @return New version
    */
  def createVersion(pending: PendingVersion)(implicit ec: ExecutionContext): Future[(Version, Channel, Seq[ProjectTag])] = {
    val project = pending.project

    val pendingVersion = pending.underlying

    for {
      // Create channel if not exists
      (channel, exists) <- (getOrCreateChannel(pending, project), pendingVersion.exists).parTupled
      _ = if (exists && this.config.projects.get[Boolean]("file-validate")) throw new IllegalArgumentException("Version already exists.")
      // Create version
      newVersion <- {
        val newVersion = Version(
          versionString = pendingVersion.versionString,
          dependencyIds = pendingVersion.dependencyIds,
          _description = pendingVersion.description,
          assets = pendingVersion.assets,
          projectId = project.id.value,
          channelId = channel.id.value,
          fileSize = pendingVersion.fileSize,
          hash = pendingVersion.hash,
          _authorId = pendingVersion.authorId,
          fileName = pendingVersion.fileName,
          signatureFileName = pendingVersion.signatureFileName
        )
        this.service.access[Version](classOf[Version]).add(newVersion)
      }
      tags <- addTags(pending, newVersion)
    } yield {
      // Notify watchers
      this.actorSystem.scheduler.scheduleOnce(Duration.Zero, NotifyWatchersTask(newVersion, project))

      project.setLastUpdated(this.service.theTime)

      uploadPlugin(project, channel, pending.plugin, newVersion)

      if (project.topicId != -1 && pending.createForumPost) {
        this.forums.postVersionRelease(project, newVersion, newVersion.description)
      }

      (newVersion, channel, tags)
    }
  }

  private def addTags(pendingVersion: PendingVersion, newVersion: Version)(implicit ec: ExecutionContext): Future[Seq[ProjectTag]] = {
    for {
      (metadataTags, dependencyTags) <- (
        addMetadataTags(pendingVersion.plugin.data, newVersion),
        addDependencyTags(newVersion)
      ).parTupled
    } yield {
      metadataTags ++ dependencyTags
    }
  }

  private def addMetadataTags(pluginFileData: Option[PluginFileData], version: Version)(implicit ec: ExecutionContext): Future[Seq[ProjectTag]] = {
    Future.sequence(pluginFileData.map(_.ghostTags.map(_.getFilledTag(service))).toList.flatten).map(
      _.map { tag =>
        tag.addVersionId(version.id.value)
        version.addTag(tag)
        tag
      })
  }

  private def addDependencyTags(version: Version)(implicit ec: ExecutionContext): Future[Seq[ProjectTag]] = {
    Future.sequence(
      Platforms.getPlatformGhostTags(
        // filter valid dependency versions
        version.dependencies.filter(d => dependencyVersionRegex.pattern.matcher(d.version).matches())
      ).map(_.getFilledTag(service))).map(
      _.map { tag =>
        tag.addVersionId(version.id.value)
        version.addTag(tag)
        tag
      })
  }

  private def getOrCreateChannel(pending: PendingVersion, project: Project)(implicit ec: ExecutionContext) = {
    project.channels.find(equalsIgnoreCase(_.name, pending.channelName))
      .getOrElseF(createChannel(project, pending.channelName, pending.channelColor, nonReviewed = false))
  }

  private def uploadPlugin(project: Project, channel: Channel, plugin: PluginFile, version: Version): Try[Unit] = Try {
    val oldPath = plugin.path
    val oldSigPath = plugin.signaturePath

    val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
    val newPath = versionDir.resolve(oldPath.getFileName)
    val newSigPath = versionDir.resolve(oldSigPath.getFileName)

    if (exists(newPath) || exists(newSigPath))
      throw InvalidPluginFileException("error.plugin.fileName")
    if (!exists(newPath.getParent))
      createDirectories(newPath.getParent)

    move(oldPath, newPath)
    move(oldSigPath, newSigPath)
    delete(oldPath)
    delete(oldSigPath)
  }

}

class OreProjectFactory @Inject()(override val service: ModelService,
                                  override val config: OreConfig,
                                  override val forums: OreDiscourseApi,
                                  override val cacheApi: SyncCacheApi,
                                  override val actorSystem: ActorSystem)
  extends ProjectFactory
