package ore.project.factory

import java.nio.file.Files._
import java.nio.file.StandardCopyOption
import javax.inject.Inject

import akka.actor.ActorSystem
import com.google.common.base.Preconditions._
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.{ProjectBase, UserBase}
import discourse.OreDiscourseApi
import models.project._
import models.project.TagColors.TagColor
import models.user.role.ProjectRole
import models.user.{Notification, User}
import ore.Colors.Color
import ore.OreConfig
import ore.permission.role.RoleTypes
import ore.project.Dependency.{ForgeId, SpongeApiId}
import ore.project.NotifyWatchersTask
import ore.project.factory.TagAlias.ProjectTag
import ore.project.io.{InvalidPluginFileException, PluginFile, PluginUpload, ProjectFiles}
import ore.user.notification.NotificationTypes
import org.spongepowered.plugin.meta.PluginMetadata
import play.api.cache.CacheApi
import play.api.i18n.{Lang, MessagesApi}
import security.pgp.PGPVerifier
import util.StringUtils._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

package object TagAlias {
  type ProjectTag = models.project.Tag
}

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit val service: ModelService
  implicit val users: UserBase = this.service.getModelBase(classOf[UserBase])
  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  val fileManager: ProjectFiles = this.projects.fileManager
  val cacheApi: CacheApi
  val actorSystem: ActorSystem
  val pgp: PGPVerifier = new PGPVerifier
  val dependencyVersionRegex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit val messages: MessagesApi
  implicit val config: OreConfig
  implicit val forums: OreDiscourseApi
  implicit val env = this.fileManager.env
  implicit val lang = Lang.defaultLang

  var isPgpEnabled = this.config.security.get[Boolean]("requirePgp")

  /**
    * Processes incoming [[PluginUpload]] data, verifies it, and loads a new
    * [[PluginFile]] for further processing.
    *
    * @param uploadData Upload data of request
    * @param owner      Upload owner
    * @return           Loaded PluginFile
    */
  def processPluginUpload(uploadData: PluginUpload, owner: User): PluginFile = {
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

    var pluginPath = uploadData.pluginFile.file.toPath
    var sigPath = uploadData.signatureFile.file.toPath

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
    plugin.loadMeta()
    plugin
  }

  def processSubsequentPluginUpload(uploadData: PluginUpload,
                                    owner: User,
                                    project: Project): Future[Either[String, PendingVersion]] = {
    val plugin = this.processPluginUpload(uploadData, owner)
    if (!plugin.meta.get.getId.equals(project.pluginId))
      return Future.successful(Left("error.version.invalidPluginId"))
    val version = for {
      projects <- project.channels.all
    } yield {
      this.startVersion(plugin, project, projects.head.name)
    }
    version.flatMap { version =>
      val model = version.underlying
      model.exists.map { exists =>
        if (exists && this.config.projects.get[Boolean]("file-validate"))
          Left("error.version.duplicate")
        else {
          // TODO cache version.cache()
          Right(version)
        }
      }
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
    * @return       PendingProject instance
    */
  def startProject(plugin: PluginFile): PendingProject = {
    val metaData = checkMeta(plugin)
    val owner = plugin.user

    // Start a new pending project
    val project = Project.Builder(this.service)
      .pluginId(metaData.getId)
      .ownerName(owner.name)
      .ownerId(owner.id.get)
      .name(metaData.getName)
      .visibility(VisibilityTypes.New)
      .build()

    val pendingProject = PendingProject(
      projects = this.projects,
      factory = this,
      underlying = project,
      file = plugin,
      config = this.config,
      channelName = this.config.getSuggestedNameForVersion(metaData.getVersion),
      cacheApi = this.cacheApi)
    pendingProject
  }

  /**
    * Starts the construction process of a [[Version]].
    *
    * @param plugin   Plugin file
    * @param project  Parent project
    * @return         PendingVersion instance
    */
  def startVersion(plugin: PluginFile, project: Project, channelName: String): PendingVersion = {
    val metaData = checkMeta(plugin)
    if (!metaData.getId.equals(project.pluginId))
      throw InvalidPluginFileException("error.plugin.invalidPluginId")

    // Create new pending version
    val depends = for (depend <- metaData.collectRequiredDependencies().asScala) yield
      depend.getId + ":" + depend.getVersion
    val path = plugin.path
    val version = Version.Builder(this.service)
      .versionString(metaData.getVersion)
      .dependencyIds(depends.toList)
      .description(metaData.getDescription)
      .projectId(project.id.getOrElse(-1)) // Version might be for an uncreated project
      .fileSize(path.toFile.length)
      .hash(plugin.md5)
      .fileName(path.getFileName.toString)
      .signatureFileName(plugin.signaturePath.getFileName.toString)
      .authorId(plugin.user.id.get)
      .build()

    PendingVersion(
      projects = this.projects,
      factory = this,
      project = project,
      channelName = channelName,
      channelColor = this.config.defaultChannelColor,
      underlying = version,
      plugin = plugin,
      // TODO remove await
      createForumPost = if (project.id.isDefined) this.service.await(project.settings).get.forumSync else true,
      )
  }

  private def checkMeta(plugin: PluginFile): PluginMetadata
  = plugin.meta.getOrElse(throw new IllegalStateException("plugin metadata not loaded?"))

  /**
    * Returns the PendingProject of the specified owner and name, if any.
    *
    * @param owner  Project owner
    * @param slug   Project slug
    * @return       PendingProject if present, None otherwise
    */
  def getPendingProject(owner: String, slug: String): Option[PendingProject]
  = this.cacheApi.get[PendingProject](owner + '/' + slug)

  /**
    * Returns the pending version for the specified owner, name, channel, and
    * version string.
    *
    * @param owner    Name of owner
    * @param slug     Project slug
    * @param version  Name of version
    * @return         PendingVersion, if present, None otherwise
    */
  def getPendingVersion(owner: String, slug: String, version: String): Option[PendingVersion]
  = this.cacheApi.get[PendingVersion](owner + '/' + slug + '/' + version)

  /**
    * Creates a new Project from the specified PendingProject
    *
    * @param pending  PendingProject
    * @return         New Project
    * @throws         IllegalArgumentException if the project already exists
    */
  def createProject(pending: PendingProject): Future[Project] = {
    val project = pending.underlying

    val checks = for {
      exists <- this.projects.exists(project)
      available <- this.projects.isNamespaceAvailable(project.ownerName, project.slug)
    } yield {
      checkArgument(!exists, "project already exists", "")
      checkArgument(available, "slug not available", "")
      checkArgument(this.config.isValidProjectName(pending.underlying.name), "invalid name", "")
    }

    // Create the project and it's settings
    for {
      _ <- checks
      newProject <- this.projects.add(pending.underlying)
    } yield {
      newProject.updateSettings(pending.settings)

      // Invite members
      val dossier = newProject.memberships
      val owner = newProject.owner
      val ownerId = owner.userId
      val projectId = newProject.id.get

      dossier.addRole(new ProjectRole(ownerId, RoleTypes.ProjectOwner, projectId, accepted = true, visible = true))
      pending.roles.map { role =>
        role.user.map { user =>
          dossier.addRole(role.copy(projectId = projectId))
          user.sendNotification(Notification(
            originId = ownerId,
            notificationType = NotificationTypes.ProjectInvite,
            message = messages("notification.project.invite", role.roleType.title, project.name)
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
    * @param project  Project to create channel for
    * @param name     Channel name
    * @param color    Channel color
    * @return         New channel
    */
  def createChannel(project: Project, name: String, color: Color, nonReviewed: Boolean): Future[Channel] = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    checkNotNull(name, "null name", "")
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    checkNotNull(color, "null color", "")
    val checks = for {
      channelCount <- project.channels.size
    } yield {
      checkState(channelCount < this.config.projects.get[Int]("max-channels"), "channel limit reached", "")
    }

    checks.flatMap { _ =>
      this.service.access[Channel](classOf[Channel]).add(new Channel(name, color, project.id.get))
    }
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending  PendingVersion
    * @return         New version
    */
  def createVersion(pending: PendingVersion): Future[Version] = {
    val project = pending.project

    val pendingVersion = pending.underlying

    val channel = for {
      // Create channel if not exists
      channel <- getOrCreateChannel(pending, project)
      exists <- pendingVersion.exists
    } yield {
      if (exists && this.config.projects.get[Boolean]("file-validate"))
        throw new IllegalArgumentException("Version already exists.")
      channel
    }

    // Create version
    val newVersion = channel.flatMap { channel =>
      val newVersion = Version(
        versionString = pendingVersion.versionString,
        dependencyIds = pendingVersion.dependencyIds,
        _description = pendingVersion.description,
        assets = pendingVersion.assets,
        projectId = project.id.get,
        channelId = channel.id.get,
        fileSize = pendingVersion.fileSize,
        hash = pendingVersion.hash,
        _authorId = pendingVersion.authorId,
        fileName = pendingVersion.fileName,
        signatureFileName = pendingVersion.signatureFileName
      )
      this.service.access[Version](classOf[Version]).add(newVersion)
    }

    for {
      channel <- channel
      newVersion <- newVersion
    } yield {
      addTags(newVersion, SpongeApiId, "Sponge", TagColors.Sponge)
      addTags(newVersion, ForgeId, "Forge", TagColors.Forge)

      // Notify watchers
      this.actorSystem.scheduler.scheduleOnce(Duration.Zero, NotifyWatchersTask(newVersion, messages))

      project.lastUpdated = this.service.theTime

      uploadPlugin(project, channel, pending.plugin, newVersion)

      if (project.topicId != -1 && pending.createForumPost) {
        this.forums.postVersionRelease(project, newVersion, newVersion.description)
      }
      newVersion
    }
  }

  private def addTags(newVersion: Version, dependencyName: String, tagName: String, tagColor: TagColor): Unit = {
    val dependenciesMatchingName = newVersion.dependencies.filter(_.pluginId == dependencyName)
    if (dependenciesMatchingName.nonEmpty) {
      val dependency = dependenciesMatchingName.head

      if (!dependencyVersionRegex.pattern.matcher(dependency.version).matches()) {
        return
      }

      for {
        tagsWithVersion <- service.access(classOf[ProjectTag])
        .filter(t => t.name === tagName && t.data === dependency.version)
      } yield {
        if (tagsWithVersion.isEmpty) {
          val tag = Tag(
            _versionIds = List(newVersion.id.get),
            name = tagName,
            data = dependency.version,
            color = tagColor
          )
          val newTag = service.access(classOf[ProjectTag]).add(tag)
          newTag.map(newVersion.addTag)

        } else {
          val tag = tagsWithVersion.head
          tag.addVersionId(newVersion.id.get)
          newVersion.addTag(tag)
        }
      }
    }
  }


  private def getOrCreateChannel(pending: PendingVersion, project: Project) = {
    project.channels.find(equalsIgnoreCase(_.name, pending.channelName)) flatMap {
      case Some(existing) => Future.successful(existing)
      case None => createChannel(project, pending.channelName, pending.channelColor, nonReviewed = false)
    }
  }

  private def uploadPlugin(project: Project, channel: Channel, plugin: PluginFile, version: Version): Try[Unit] = Try {
    val meta = plugin.meta.get

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
                                  override val cacheApi: CacheApi,
                                  override val messages: MessagesApi,
                                  override val actorSystem: ActorSystem)
                                  extends ProjectFactory
