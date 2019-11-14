package ore.models.project.factory

import javax.inject.{Inject, Singleton}

import play.api.cache.SyncCacheApi
import play.api.i18n.Messages

import db.impl.access.ProjectBase
import discourse.OreDiscourseApi
import ore.data.user.notification.NotificationType
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.VersionTable
import ore.db.{DbRef, Model, ModelService}
import ore.member.MembershipDossier
import ore.models.project._
import ore.models.project.io._
import ore.models.user.role.ProjectUserRole
import ore.models.user.{Notification, User}
import ore.permission.role.Role
import ore.util.OreMDC
import ore.util.StringUtils._
import ore.{OreConfig, OreEnv}
import util.FileIO
import util.syntax._

import cats.data.NonEmptyList
import cats.syntax.all._
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

  implicit protected def config: OreConfig
  implicit protected def forums: OreDiscourseApi[UIO]
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
  private def processPluginUpload(uploadData: PluginUpload, owner: Model[User])(
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
        uploadData.pluginFile.moveFileTo(tmpDir.resolve(pluginFileName), replace = true)
      )

      val loadData = createDirs *> moveToNewPluginPath.flatMap { newPluginPath =>
        // create and load a new PluginFile instance for further processing
        val plugin = new PluginFile(newPluginPath, owner)
        plugin.loadMeta[Task]
      }

      loadData.orDie.absolve
    }
  }

  /**
    * Returns true if a project ID is defined on this Model, there is no
    * matching hash in the Project, and there is no duplicate version with
    * the same name in the Project.
    *
    * @return True if exists
    */
  private def versionExists(projectId: DbRef[Project], hash: String, versionString: String): UIO[Boolean] = {
    val hashExistsBaseQuery = for {
      v <- TableQuery[VersionTable]
      if v.projectId === projectId
      if v.hash === hash
    } yield v.id

    val hashExistsQuery = hashExistsBaseQuery.exists

    for {
      project <- ModelView
        .now(Project)
        .get(projectId)
        .getOrElseF(ZIO.dieMessage(s"No project found for id $projectId"))
      versionExistsQuery = project
        .versions(ModelView.later(Version))
        .exists(_.versionString.toLowerCase === versionString.toLowerCase)
      res <- service.runDBIO(Query((hashExistsQuery, versionExistsQuery)).map(t => t._1 && t._2).result.head)
    } yield res
  }

  def collectErrorsForVersionUpload(uploadData: PluginUpload, uploader: Model[User], project: Model[Project])(
      implicit messages: Messages
  ): ZIO[Blocking, String, PluginFileWithData] =
    for {
      _ <- ZIO.fromOption(hasUserUploadError(uploader)).flip
      plugin <- processPluginUpload(uploadData, uploader)
        .ensure("error.version.invalidPluginId")(_.data.id.contains(project.pluginId))
        .ensure("error.version.illegalVersion")(!_.data.version.contains("recommended"))
      _             <- ZIO.unit.filterOrFail(_ => plugin.data.id.contains(project.pluginId))("error.plugin.invalidPluginId")
      _             <- ZIO.unit.filterOrFail(_ => plugin.data.version.isDefined)("error.plugin.noVersion")
      versionExists <- versionExists(project.id, plugin.md5, plugin.versionString)
      _ <- {
        if (versionExists && this.config.ore.projects.fileValidate) ZIO.fail("error.version.duplicate")
        else ZIO.unit
      }
    } yield plugin

  /**
    * Returns the error ID to display to the User, if any, if they cannot
    * upload files.
    *
    * @return Upload error if any
    */
  def hasUserUploadError(user: User): Option[String] =
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
    * @param plugin The plugin file
    * @return New version
    */
  def createVersion(
      project: Model[Project],
      plugin: PluginFileWithData,
      description: Option[String],
      createForumPost: Boolean,
      stability: Version.Stability,
      releaseType: Option[Version.ReleaseType]
  ): ZIO[Blocking, NonEmptyList[String], (Model[Project], Model[Version])] = {

    for {
      // Create version
      version <- service.insert(plugin.asVersion(project.id, description, createForumPost, stability, releaseType))
      // Notify watchers
      _ <- notifyWatchers(version, project)
      _ <- uploadPluginFile(project, plugin, version).orDieWith(s => new Exception(s))
      firstTimeUploadProject <- {
        if (project.visibility == Visibility.New) {
          val setVisibility = (project: Model[Project]) => {
            project
              .setVisibility(Visibility.Public, "First upload", version.authorId.getOrElse(project.ownerId))
              .map(_._1)
          }

          val initProject =
            if (project.topicId.isEmpty) this.forums.createProjectTopic(project).flatMap(setVisibility)
            else setVisibility(project)

          initProject <* projects.refreshHomePage(MDCLogger)(OreMDC.NoMDC)

        } else UIO.succeed(project)
      }
      withTopicId <- if (firstTimeUploadProject.topicId.isDefined && createForumPost)
        this.forums.createVersionPost(firstTimeUploadProject, version)
      else UIO.succeed(version)
    } yield (firstTimeUploadProject, withTopicId)
  }

  private def uploadPluginFile(
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

    fileIO.exists(newPath).ifM(UIO.succeed(Left("error.plugin.fileName")), move).orDie.absolve
  }

}

@Singleton
class OreProjectFactory @Inject()(
    val service: ModelService[UIO],
    val config: OreConfig,
    val forums: OreDiscourseApi[UIO],
    val cacheApi: SyncCacheApi,
    val env: OreEnv,
    val projects: ProjectBase[UIO],
    val fileManager: ProjectFiles[ZIO[Blocking, Nothing, *]],
    val fileIO: FileIO[ZIO[Blocking, Nothing, *]]
) extends ProjectFactory
