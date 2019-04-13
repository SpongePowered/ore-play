package ore.project.factory

import java.nio.file.Files._
import java.nio.file.StandardCopyOption
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.matching.Regex

import play.api.cache.SyncCacheApi
import play.api.i18n.Messages

import db.access.ModelView
import db.impl.access.ProjectBase
import db.impl.OrePostgresDriver.api._
import db.{DbRef, Model, ModelService}
import discourse.OreDiscourseApi
import form.project.ProjectCreateForm
import models.project._
import models.user.User
import ore.project.NotifyWatchersTask
import ore.project.io._
import ore.{Color, OreConfig, OreEnv, Platform}
import security.pgp.PGPVerifier
import util.{OreMDC, StringUtils}
import util.StringUtils._

import akka.actor.ActorSystem
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.google.common.base.Preconditions._

/**
  * Manages the project and version creation pipeline.
  */
trait ProjectFactory {

  implicit def service: ModelService
  implicit def projects: ProjectBase = ProjectBase.fromService

  def fileManager: ProjectFiles = this.projects.fileManager
  def cacheApi: SyncCacheApi
  def actorSystem: ActorSystem
  val pgp: PGPVerifier              = new PGPVerifier
  val dependencyVersionRegex: Regex = "^[0-9a-zA-Z\\.\\,\\[\\]\\(\\)-]+$".r

  implicit def config: OreConfig
  implicit def forums: OreDiscourseApi
  implicit def env: OreEnv = this.fileManager.env

  val isPgpEnabled: Boolean = this.config.security.requirePgp

  /**
    * Processes incoming [[PluginUpload]] data, verifies it, and loads a new
    * [[PluginFile]] for further processing.
    *
    * @param uploadData Upload data of request
    * @param owner      Upload owner
    * @return Loaded PluginFile
    */
  def processPluginUpload(uploadData: PluginUpload, owner: Model[User])(
      implicit messages: Messages,
      mdc: OreMDC
  ): EitherT[IO, String, PluginFileWithData] = {
    val pluginFileName    = uploadData.pluginFileName
    val signatureFileName = uploadData.signatureFileName

    // file extension constraints
    if (!pluginFileName.endsWith(".zip") && !pluginFileName.endsWith(".jar"))
      EitherT.leftT("error.plugin.fileExtension")
    else if (!signatureFileName.endsWith(".sig") && !signatureFileName.endsWith(".asc"))
      EitherT.leftT("error.plugin.sig.fileExtension")
    // check user's public key validity
    else if (owner.pgpPubKey.isEmpty)
      EitherT.leftT("error.plugin.noPubKey")
    else if (!owner.isPgpPubKeyReady)
      EitherT.leftT("error.plugin.pubKey.cooldown")
    else {
      val pluginPath = uploadData.pluginFile.path
      val sigPath    = uploadData.signatureFile.path

      // verify detached signature
      if (!this.pgp.verifyDetachedSignature(pluginPath, sigPath, owner.pgpPubKey.get))
        EitherT.leftT("error.plugin.sig.failed")
      else {
        // move uploaded files to temporary directory while the project creation
        // process continues
        val tmpDir = this.env.tmp.resolve(owner.name)
        if (notExists(tmpDir))
          createDirectories(tmpDir)

        val signatureFileExtension = signatureFileName.substring(signatureFileName.lastIndexOf("."))
        val newSignatureFileName   = pluginFileName + signatureFileExtension
        val newPluginPath          = copy(pluginPath, tmpDir.resolve(pluginFileName), StandardCopyOption.REPLACE_EXISTING)
        val newSigPath             = copy(sigPath, tmpDir.resolve(newSignatureFileName), StandardCopyOption.REPLACE_EXISTING)

        // create and load a new PluginFile instance for further processing
        val plugin = new PluginFile(newPluginPath, newSigPath, owner)
        plugin.loadMeta
      }
    }
  }

  def processSubsequentPluginUpload(uploadData: PluginUpload, owner: Model[User], project: Model[Project])(
      implicit messages: Messages,
      cs: ContextShift[IO],
      mdc: OreMDC
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
      (isPgpEnabled && user.pgpPubKey.isEmpty) -> "error.pgp.noPubKey",
      (isPgpEnabled && !user.isPgpPubKeyReady) -> "error.pgp.keyChangeCooldown",
      user.isLocked                            -> "error.user.locked"
    ).find(_._1).map(_._2)

  def createProject(owner: Model[User], settings: ProjectCreateForm)(
      implicit cs: ContextShift[IO]
  ): EitherT[IO, String, (Project, ProjectSettings)] = {
    val name = settings.name
    val slug = slugify(name)

    val project = Project(
      pluginId = settings.pluginId,
      ownerName = owner.name,
      ownerId = owner.id,
      name = name,
      slug = slug,
      category = settings.category,
      description = settings.description,
      visibility = Visibility.New,
    )

    val projectSettings: DbRef[Project] => ProjectSettings = ProjectSettings(_)
    val channel: DbRef[Project] => Channel                 = Channel(_, config.defaultChannelName, config.defaultChannelColor)

    for {
      t <- EitherT.liftF(
        (
          this.projects.exists(owner.name, name),
          this.projects.isNamespaceAvailable(owner.name, slug)
        ).parTupled
      )
      (exists, available) = t
      _           <- EitherT.cond[IO].apply(!exists, (), "project already exists")
      _           <- EitherT.cond[IO].apply(available, (), "slug not available")
      _           <- EitherT.cond[IO].apply(config.isValidProjectName(name), (), "invalid name")
      newProject  <- EitherT.right[String](service.insert(project))
      newSettings <- EitherT.right[String](service.insert(projectSettings(newProject.id.value)))
      _           <- EitherT.right[String](service.insert(channel(newProject.id.value)))
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
          signatureFileName = plugin.signaturePath.getFileName.toString,
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
          val setVisibility = project.setVisibility(Visibility.Public, "First upload", version.authorId).map(_._1)
          if (project.topicId.isEmpty) this.forums.createProjectTopic(project) *> setVisibility else setVisibility
        } else IO.pure(project)
      }
      withTopicId <- if (firstTimeUploadProject.topicId.isDefined && pending.createForumPost)
        this.forums
          .createVersionPost(firstTimeUploadProject, version)
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
        val oldPath    = plugin.path
        val oldSigPath = plugin.signaturePath

        val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
        val newPath    = versionDir.resolve(oldPath.getFileName)
        val newSigPath = versionDir.resolve(oldSigPath.getFileName)

        if (exists(newPath) || exists(newSigPath))
          Left("error.plugin.fileName")
        else {
          if (!exists(newPath.getParent))
            createDirectories(newPath.getParent)

          move(oldPath, newPath)
          move(oldSigPath, newSigPath)
          deleteIfExists(oldPath)
          deleteIfExists(oldSigPath)
          Right(())
        }
      }
    )

}

class OreProjectFactory @Inject()(
    override val service: ModelService,
    override val config: OreConfig,
    override val forums: OreDiscourseApi,
    override val cacheApi: SyncCacheApi,
    override val actorSystem: ActorSystem
) extends ProjectFactory
