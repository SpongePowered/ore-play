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
import play.api.i18n.MessagesApi
import security.pgp.PGPVerifier
import util.StringUtils._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
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

  var isPgpEnabled = this.config.security.getBoolean("requirePgp").get

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
                                    project: Project): Either[String, PendingVersion] = {
    val plugin = this.processPluginUpload(uploadData, owner)
    if (!plugin.meta.get.getId.equals(project.pluginId))
      return Left("error.version.invalidPluginId")
    val version = this.startVersion(plugin, project, project.channels.all.head.name)
    val model = version.underlying
    if (model.exists && this.config.projects.getBoolean("file-validate").get)
      return Left("error.version.duplicate")
    version.cache()
    Right(version)
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
      cacheApi = this.cacheApi)
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
  def createProject(pending: PendingProject): Try[Project] = Try {
    val project = pending.underlying
    checkArgument(!this.projects.exists(project), "project already exists", "")
    checkArgument(this.projects.isNamespaceAvailable(project.ownerName, project.slug), "slug not available", "")
    checkArgument(this.config.isValidProjectName(pending.underlying.name), "invalid name", "")

    // Create the project and it's settings
    val newProject = this.projects.add(pending.underlying)
    newProject.settings = pending.settings

    // Invite members
    val dossier = newProject.memberships
    val owner = newProject.owner
    val ownerId = owner.id.get
    val projectId = newProject.id.get

    dossier.addRole(new ProjectRole(ownerId, RoleTypes.ProjectOwner, projectId, accepted = true, visible = true))
    for (role <- pending.roles) {
      val user = role.user
      dossier.addRole(role.copy(projectId = projectId))
      user.sendNotification(Notification(
        originId = ownerId,
        notificationType = NotificationTypes.ProjectInvite,
        message = messages("notification.project.invite", role.roleType.title, project.name)
      ))
    }

    Try(this.forums.await(this.forums.createProjectTopic(newProject)))

    newProject
  }

  /**
    * Creates a new release channel for the specified [[Project]].
    *
    * @param project  Project to create channel for
    * @param name     Channel name
    * @param color    Channel color
    * @return         New channel
    */
  def createChannel(project: Project, name: String, color: Color, nonReviewed: Boolean): Channel = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    checkNotNull(name, "null name", "")
    checkArgument(this.config.isValidChannelName(name), "invalid name", "")
    checkNotNull(color, "null color", "")
    checkState(project.channels.size < this.config.projects.getInt("max-channels").get, "channel limit reached", "")
    this.service.access[Channel](classOf[Channel]).add(new Channel(name, color, project.id.get))
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending  PendingVersion
    * @return         New version
    */
  def createVersion(pending: PendingVersion): Try[Version] = Try {
    var channel: Channel = null
    val project = pending.project

    // Create channel if not exists
    project.channels.find(equalsIgnoreCase(_.name, pending.channelName)) match {
      case None =>
        channel = createChannel(project, pending.channelName, pending.channelColor, nonReviewed = false)
      case Some(existing) =>
        channel = existing
    }

    // Create version
    val pendingVersion = pending.underlying
    if (pendingVersion.exists && this.config.projects.getBoolean("file-validate").get)
      throw new IllegalArgumentException("Version already exists.")

    val newVersion = this.service.access[Version](classOf[Version]).add(Version(
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
    ))

    def addTags(dependencyName: String, tagName: String, tagColor: TagColor): Unit = {
      val dependenciesMatchingName = newVersion.dependencies.filter(_.pluginId == dependencyName)
      if (dependenciesMatchingName.nonEmpty) {
        val dependency = dependenciesMatchingName.head

        if (!dependencyVersionRegex.pattern.matcher(dependency.version).matches()) {
          return
        }

        val tagsWithVersion = service.access(classOf[ProjectTag])
          .filter(t => t.name === tagName && t.data === dependency.version).toList

        if (tagsWithVersion.isEmpty) {
          val tag = Tag(
            _versionIds = List(newVersion.id.get),
            name = tagName,
            data = dependency.version,
            color = tagColor
          )
          service.access(classOf[ProjectTag]).add(tag)
          // requery the tag because it now includes the id
          val newTag = service.access(classOf[ProjectTag]).filter(t => t.name === tag.name && t.data === tag.data).toList.head
          newVersion.addTag(newTag)
        } else {
          val tag = tagsWithVersion.head
          tag.addVersionId(newVersion.id.get)
          newVersion.addTag(tag)
        }
      }
    }

    addTags(SpongeApiId, "Sponge", TagColors.Sponge)
    addTags(ForgeId, "Forge", TagColors.Forge)

    // Notify watchers
    this.actorSystem.scheduler.scheduleOnce(Duration.Zero, NotifyWatchersTask(newVersion, messages))

    project.lastUpdated = this.service.theTime

    uploadPlugin(project, channel, pending.plugin, newVersion)
    newVersion
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
