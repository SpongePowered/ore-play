package controllers.project

import java.nio.file.Files._
import java.nio.file.{Files, StandardCopyOption}
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc
import play.api.mvc.{Action, AnyContent, Result}
import play.filters.csrf.CSRF

import controllers.sugar.Requests.{AuthRequest, OreRequest, ProjectRequest}
import controllers.{OreBaseController, OreControllerComponents}
import discourse.OreDiscourseApi
import form.OreForms
import models.viewhelper.VersionData
import ore.data.DownloadType
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.UserTable
import ore.db.{DbRef, Model}
import ore.markdown.MarkdownRenderer
import ore.models.admin.VersionVisibilityChange
import ore.models.project._
import ore.models.project.factory.ProjectFactory
import ore.models.project.io.{PluginFile, PluginUpload, ProjectFiles}
import ore.models.user.{LoggedAction, User}
import ore.permission.Permission
import ore.util.OreMDC
import ore.util.StringUtils._
import ore.{OreEnv, StatTracker}
import util.UserActionLogger
import util.syntax._
import views.html.projects.{versions => views}

import cats.instances.option._
import cats.syntax.all._
import com.github.tminglei.slickpg.InetString
import com.typesafe.scalalogging
import _root_.io.circe.Json
import _root_.io.circe.syntax._
import scalaz.zio.blocking.Blocking
import scalaz.zio
import scalaz.zio.{IO, Task, UIO, ZIO}
import scalaz.zio.interop.catz._

/**
  * Controller for handling Version related actions.
  */
@Singleton
class Versions @Inject()(stats: StatTracker[UIO], forms: OreForms, factory: ProjectFactory)(
    implicit oreComponents: OreControllerComponents,
    messagesApi: MessagesApi,
    env: OreEnv,
    forums: OreDiscourseApi[UIO],
    renderer: MarkdownRenderer,
    fileManager: ProjectFiles
) extends OreBaseController {

  private val self = controllers.project.routes.Versions

  private val Logger    = scalalogging.Logger("Versions")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  private def VersionEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(Permission.EditVersion))

  private def VersionUploadAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(Permission.CreateVersion))

  /**
    * Shows the specified version view page.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Version view
    */
  def show(author: String, slug: String, versionString: String): Action[AnyContent] =
    ProjectAction(author, slug).asyncBIO { implicit request =>
      for {
        version  <- getVersion(request.project, versionString)
        data     <- VersionData.of[Task, zio.interop.ParIO[Any, Throwable, ?]](request, version).orDie
        response <- this.stats.projectViewed(UIO.succeed(Ok(views.view(data, request.scoped))))
      } yield response
    }

  /**
    * Saves the specified Version's description.
    *
    * @param author        Project owner
    * @param slug          Project slug
    * @param versionString Version name
    * @return View of Version
    */
  def saveDescription(author: String, slug: String, versionString: String): Action[String] = {
    VersionEditAction(author, slug).asyncBIO(parse.form(forms.VersionDescription)) { implicit request =>
      for {
        version <- getVersion(request.project, versionString)
        oldDescription = version.description.getOrElse("")
        newDescription = request.body.trim
        _ <- version.updateForumContents[Task](newDescription).orDie
        _ <- UserActionLogger.log(
          request.request,
          LoggedAction.VersionDescriptionEdited,
          version.id,
          newDescription,
          oldDescription
        )
      } yield Redirect(self.show(author, slug, versionString))
    }
  }

  /**
    * Sets the specified Version as the recommended download.
    *
    * @param author         Project owner
    * @param slug           Project slug
    * @param versionString  Version name
    * @return               View of version
    */
  def setRecommended(author: String, slug: String, versionString: String): Action[AnyContent] = {
    VersionEditAction(author, slug).asyncBIO { implicit request =>
      for {
        version <- getVersion(request.project, versionString)
        _       <- service.update(request.project)(_.copy(recommendedVersionId = Some(version.id)))
        _ <- UserActionLogger.log(
          request.request,
          LoggedAction.VersionAsRecommended,
          version.id,
          "recommended version",
          "listed version"
        )
      } yield Redirect(self.show(author, slug, versionString))
    }
  }

  /**
    * Sets the specified Version as approved by the moderation staff.
    *
    * @param author         Project owner
    * @param slug           Project slug
    * @param versionString  Version name
    * @return               View of version
    */
  def approve(author: String, slug: String, versionString: String, partial: Boolean): Action[AnyContent] = {
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.Reviewer))
      .asyncBIO { implicit request =>
        val newState = if (partial) ReviewState.PartiallyReviewed else ReviewState.Reviewed
        for {
          version <- getVersion(request.data.project, versionString)
          _ <- service.update(version)(
            _.copy(
              reviewState = newState,
              reviewerId = Some(request.user.id),
              approvedAt = Some(Instant.now())
            )
          )
          _ <- UserActionLogger.log(
            request.request,
            LoggedAction.VersionReviewStateChanged,
            version.id,
            newState.toString,
            version.reviewState.toString,
          )
        } yield Redirect(self.show(author, slug, versionString))
      }
  }

  /**
    * Displays the "versions" tab within a Project view.
    *
    * @param author   Owner of project
    * @param slug     Project slug
    * @return View of project
    */
  def showList(author: String, slug: String): Action[AnyContent] = {
    ProjectAction(author, slug).asyncF { implicit request =>
      val allChannelsDBIO = request.project.channels(ModelView.raw(Channel)).result

      service.runDBIO(allChannelsDBIO).flatMap { allChannels =>
        this.stats.projectViewed(
          UIO.succeed(
            Ok(
              views.list(
                request.data,
                request.scoped,
                Model.unwrapNested(allChannels)
              )
            )
          )
        )
      }
    }
  }

  /**
    * Shows the creation form for new versions on projects.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return Version creation view
    */
  def showCreator(author: String, slug: String): Action[AnyContent] =
    VersionUploadAction(author, slug).asyncF { implicit request =>
      service.runDBIO(request.project.channels(ModelView.raw(Channel)).result).map { channels =>
        val project = request.project
        Ok(
          views.create(
            project.name,
            project.pluginId,
            project.slug,
            project.ownerName,
            project.description,
            forumSync = request.data.settings.forumSync,
            None,
            Model.unwrapNested(channels)
          )
        )
      }
    }

  /**
    * Uploads a new version for a project for further processing.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @return Version create page (with meta)
    */
  def upload(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).asyncBIO {
    implicit request =>
      val call = self.showCreator(author, slug)
      val user = request.user

      val uploadData = this.factory
        .getUploadError(user)
        .map(error => Redirect(call).withError(error))
        .toLeft(())
        .flatMap(_ => PluginUpload.bindFromRequest().toRight(Redirect(call).withError("error.noFile")))

      ZIO
        .fromEither(uploadData)
        .flatMap { data =>
          this.factory
            .processSubsequentPluginUpload(data, user, request.data.project)
            .mapError(err => Redirect(call).withError(err))
        }
        .flatMap { pendingVersion =>
          pendingVersion
            .copy(authorId = user.id)
            .cache[Task]
            .orDie
            .const(
              Redirect(
                self.showCreatorWithMeta(request.data.project.ownerName, slug, pendingVersion.versionString)
              )
            )
        }
  }

  /**
    * Displays the "version create" page with the associated plugin meta-data.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Version create view
    */
  def showCreatorWithMeta(author: String, slug: String, versionString: String): Action[AnyContent] =
    UserLock(ShowProject(author, slug)).asyncBIO { implicit request =>
      val success = ZIO
        .fromOption(this.factory.getPendingVersion(author, slug, versionString))
        // Get pending version
        .flatMap(pendingVersion => projects.withSlug(author, slug).value.get.tupleLeft(pendingVersion))

      val suc2 = success
        .flatMap {
          case (pendingVersion, project) =>
            val projectData = project.settings[Task].orDie.map { settings =>
              (project.name, project.pluginId, project.slug, project.ownerName, project.description, settings.forumSync)
            }
            (service.runDBIO(project.channels(ModelView.raw(Channel)).result), projectData)
              .parMapN((channels, data) => (channels, data, pendingVersion))
        }
        .map {
          case (
              channels,
              (projectName, pluginId, projectSlug, ownerName, projectDescription, forumSync),
              pendingVersion
              ) =>
            Ok(
              views.create(
                projectName,
                pluginId,
                projectSlug,
                ownerName,
                projectDescription,
                forumSync,
                Some(pendingVersion),
                Model.unwrapNested(channels)
              )
            )
        }

      suc2.mapError(_ => Redirect(self.showCreator(author, slug)).withError("error.plugin.timeout"))
    }

  /**
    * Completes the creation of the specified pending version or project if
    * first version.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return New version view
    */
  def publish(author: String, slug: String, versionString: String): Action[AnyContent] = {
    UserLock(ShowProject(author, slug)).asyncBIO { implicit request =>
      // First get the pending Version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          // Not found
          IO.fail(Redirect(self.showCreator(author, slug)).withError("error.plugin.timeout"))
        case Some(pendingVersion) =>
          // Get submitted channel
          this.forms.VersionCreate.bindFromRequest.fold(
            // Invalid channel
            FormError(self.showCreatorWithMeta(author, slug, versionString)).andThen(IO.fail),
            versionData => {
              // Channel is valid

              val newPendingVersion = pendingVersion.copy(
                channelName = versionData.channelName.trim,
                channelColor = versionData.color,
                createForumPost = versionData.forumPost,
                description = versionData.content
              )

              val createVersion = getProject(author, slug).flatMap {
                project =>
                  project
                    .channels(ModelView.now(Channel))
                    .find(equalsIgnoreCase(_.name, newPendingVersion.channelName))
                    .value
                    .flatMap(_.fold(versionData.addTo[Task](project).value.orDie.absolve)(ZIO.succeed))
                    .zipRight {
                      //Breaking up to get around IntelliJ
                      val res = newPendingVersion
                        .complete(project, factory)
                        .map(t => t._1 -> t._2)
                        .tap {
                          case (newProject, newVersion) =>
                            if (versionData.recommended)
                              service
                                .update(newProject)(
                                  _.copy(
                                    recommendedVersionId = Some(newVersion.id),
                                    lastUpdated = Instant.now()
                                  )
                                )
                                .unit
                            else
                              service
                                .update(newProject)(
                                  _.copy(
                                    lastUpdated = Instant.now()
                                  )
                                )
                                .unit
                        }

                      val res2 = res
                        .tap(t => addUnstableTag(t._2, versionData.unstable))

                      res2
                        .tap {
                          case (_, newVersion) =>
                            UserActionLogger.log(
                              request,
                              LoggedAction.VersionUploaded,
                              newVersion.id,
                              "published",
                              "null"
                            )
                        }
                        .const(Redirect(self.show(author, slug, versionString)))
                    }
                    .mapError(Redirect(self.showCreatorWithMeta(author, slug, versionString)).withErrors(_))
              }

              (newPendingVersion.exists[Task].orDie: ZIO[Blocking, Result, Boolean]).ifM(
                IO.fail(Redirect(self.showCreator(author, slug)).withError("error.plugin.versionExists")),
                createVersion
              )
            }
          )
      }
    }
  }

  private def addUnstableTag(version: Model[Version], unstable: Boolean) = {
    if (unstable) {
      service
        .insert(
          VersionTag(
            versionId = version.id,
            name = "Unstable",
            data = "",
            color = TagColor.Unstable
          )
        )
        .unit
    } else UIO.unit
  }

  /**
    * Deletes the specified version and returns to the version page.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Versions page
    */
  def delete(author: String, slug: String, versionString: String): Action[String] = {
    Authenticated
      .andThen(PermissionAction[AuthRequest](Permission.HardDeleteVersion))
      .asyncBIO(parse.form(forms.NeedsChanges)) { implicit request =>
        val comment = request.body
        getProjectVersion(author, slug, versionString)
          .flatMap(version => projects.deleteVersion(version).const(version))
          .flatMap { version =>
            UserActionLogger
              .log(
                request,
                LoggedAction.VersionDeleted,
                version.id,
                s"Deleted: $comment",
                s"$version.visibility"
              )
          }
          .const(Redirect(self.showList(author, slug)))
      }
  }

  /**
    * Soft deletes the specified version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def softDelete(author: String, slug: String, versionString: String): Action[String] =
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.DeleteVersion))
      .asyncBIO(parse.form(forms.NeedsChanges)) { implicit request =>
        val comment = request.body
        getVersion(request.project, versionString)
          .flatMap(version => projects.prepareDeleteVersion(version).as(version))
          .flatMap(version => version.setVisibility(Visibility.SoftDelete, comment, request.user.id).const(version))
          .flatMap { version =>
            UserActionLogger.log(request.request, LoggedAction.VersionDeleted, version.id, s"SoftDelete: $comment", "")
          }
          .const(Redirect(self.showList(author, slug)))
      }

  /**
    * Restore the specified version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def restore(author: String, slug: String, versionString: String): Action[String] = {
    Authenticated
      .andThen(PermissionAction[AuthRequest](Permission.Reviewer))
      .asyncBIO(parse.form(forms.NeedsChanges)) { implicit request =>
        val comment = request.body
        getProjectVersion(author, slug, versionString)
          .flatMap(version => version.setVisibility(Visibility.Public, comment, request.user.id).const(version))
          .flatMap { version =>
            UserActionLogger.log(request, LoggedAction.VersionDeleted, version.id, s"Restore: $comment", "")
          }
          .const(Redirect(self.showList(author, slug)))
      }
  }

  def showLog(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated
      .andThen(PermissionAction[AuthRequest](Permission.ViewLogs))
      .andThen(ProjectAction(author, slug))
      .asyncBIO { implicit request =>
        for {
          version <- getVersion(request.project, versionString)
          visChanges <- service.runDBIO(
            version
              .visibilityChangesByDate(ModelView.raw(VersionVisibilityChange))
              .joinLeft(TableQuery[UserTable])
              .on(_.createdBy === _.id)
              .result
          )
        } yield
          Ok(
            views.log(
              request.project,
              version,
              Model.unwrapNested[Seq[(Model[VersionVisibilityChange], Option[User])]](visChanges)
            )
          )
      }
  }

  /**
    * Sends the specified Project Version to the client.
    *
    * @param author        Project owner
    * @param slug          Project slug
    * @param versionString Version string
    * @return Sent file
    */
  def download(author: String, slug: String, versionString: String, token: Option[String]): Action[AnyContent] =
    ProjectAction(author, slug).asyncBIO { implicit request =>
      val project = request.project
      getVersion(project, versionString).flatMap(sendVersion(project, _, token))
    }

  private def sendVersion(project: Project, version: Model[Version], token: Option[String])(
      implicit req: ProjectRequest[_]
  ): UIO[Result] = {
    checkConfirmation(version, token).flatMap { passed =>
      if (passed)
        _sendVersion(project, version)
      else
        UIO.succeed(
          Redirect(
            self.showDownloadConfirm(
              project.ownerName,
              project.slug,
              version.name,
              Some(DownloadType.UploadedFile.value),
              api = Some(false),
              Some("dummy")
            )
          )
        )
    }
  }

  private def checkConfirmation(version: Model[Version], token: Option[String])(
      implicit req: ProjectRequest[_]
  ): UIO[Boolean] = {
    if (version.reviewState == ReviewState.Reviewed)
      UIO.succeed(true)
    else {
      val hasSessionConfirm = req.session.get(DownloadWarning.cookieKey(version.id)).contains("confirmed")

      if (hasSessionConfirm) {
        UIO.succeed(true)
      } else {
        // check confirmation for API
        ZIO
          .fromOption(token)
          .flatMap { tkn =>
            ModelView
              .now(DownloadWarning)
              .find { warn =>
                (warn.token === tkn) &&
                (warn.versionId === version.id.value) &&
                (warn.address === InetString(StatTracker.remoteAddress)) &&
                warn.isConfirmed
              }
              .value
              .get
          }
          .flatMap(warn => if (warn.hasExpired) service.delete(warn).const(false) else UIO.succeed(true))
          .catchAll(_ => UIO.succeed(false))
      }
    }
  }

  private def _sendVersion(project: Project, version: Model[Version])(implicit req: ProjectRequest[_]): UIO[Result] =
    this.stats.versionDownloaded(version) {
      UIO.succeed {
        Ok.sendPath(
          this.fileManager
            .getVersionDir(project.ownerName, project.name, version.name)
            .resolve(version.fileName)
        )
      }
    }

  private val MultipleChoices = new Status(MULTIPLE_CHOICES)

  /**
    * Displays a confirmation view for downloading unreviewed versions. The
    * client is issued a unique token that will be checked once downloading to
    * ensure that they have landed on this confirmation before downloading the
    * version.
    *
    * @param author Project author
    * @param slug   Project slug
    * @param target Target version
    * @param dummy  A parameter to get around Chrome's cache
    * @return       Confirmation view
    */
  def showDownloadConfirm(
      author: String,
      slug: String,
      target: String,
      downloadType: Option[Int],
      api: Option[Boolean],
      dummy: Option[String]
  ): Action[AnyContent] = {
    ProjectAction(author, slug).asyncBIO { implicit request =>
      val dlType              = downloadType.flatMap(DownloadType.withValueOpt).getOrElse(DownloadType.UploadedFile)
      implicit val lang: Lang = request.lang
      val project             = request.project
      getVersion(project, target)
        .ensure(Redirect(ShowProject(author, slug)).withError("error.plugin.stateChanged"))(
          _.reviewState != ReviewState.Reviewed
        )
        .flatMap { version =>
          // generate a unique "warning" object to ensure the user has landed
          // on the warning before downloading
          val token      = UUID.randomUUID().toString
          val expiration = Instant.now().plusMillis(this.config.security.unsafeDownloadMaxAge)
          val address    = InetString(StatTracker.remoteAddress)
          // remove old warning attached to address that are expired (or duplicated for version)
          val removeWarnings = service.deleteWhere(DownloadWarning) { warning =>
            (warning.address === address || warning.expiration < Instant
              .now()) && warning.versionId === version.id.value
          }
          // create warning
          val addWarning = service.insert(
            DownloadWarning(
              expiration = expiration,
              token = token,
              versionId = version.id,
              address = address,
              downloadId = None
            )
          )

          val isPartial   = version.reviewState == ReviewState.PartiallyReviewed
          val apiMsgKey   = if (isPartial) "version.download.confirmPartial.api" else "version.download.confirm.body.api"
          lazy val apiMsg = this.messagesApi(apiMsgKey)

          lazy val curlInstruction = this.messagesApi(
            "version.download.confirm.curl",
            self.confirmDownload(author, slug, target, Some(dlType.value), Some(token), None).absoluteURL(),
            CSRF.getToken.get.value
          )

          if (api.getOrElse(false)) {
            (removeWarnings *> addWarning).as(
              MultipleChoices(
                Json
                  .obj(
                    "message" := apiMsg,
                    "post" := self
                      .confirmDownload(author, slug, target, Some(dlType.value), Some(token), None)
                      .absoluteURL(),
                    "url" := self.downloadJarById(project.pluginId, version.name, Some(token)).absoluteURL(),
                    "curl" := curlInstruction,
                    "token" := token
                  )
                  .spaces4
              ).withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\"")
            )
          } else {
            val userAgent = request.headers.get("User-Agent").map(_.toLowerCase)

            if (userAgent.exists(_.startsWith("wget/"))) {
              IO.succeed(
                MultipleChoices(this.messagesApi("version.download.confirm.wget"))
                  .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\"")
              )
            } else if (userAgent.exists(_.startsWith("curl/"))) {
              (removeWarnings *> addWarning).as(
                MultipleChoices(
                  apiMsg + "\n" + curlInstruction + "\n"
                ).withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\"")
              )
            } else {
              version.channel[Task].orDie.map(_.isNonReviewed).map { nonReviewed =>
                //We return Ok here to make sure Chrome sets the cookie
                //https://bugs.chromium.org/p/chromium/issues/detail?id=696204
                Ok(views.unsafeDownload(project, version, nonReviewed, dlType))
                  .addingToSession(DownloadWarning.cookieKey(version.id) -> "set")
              }
            }
          }
        }
    }
  }

  def confirmDownload(
      author: String,
      slug: String,
      target: String,
      downloadType: Option[Int],
      token: Option[String],
      dummy: Option[String] //A parameter to get around Chrome's cache
  ): Action[AnyContent] = {
    ProjectAction(author, slug).asyncBIO { implicit request =>
      getVersion(request.data.project, target)
        .ensure(Redirect(ShowProject(author, slug)).withError("error.plugin.stateChanged"))(
          _.reviewState != ReviewState.Reviewed
        )
        .flatMap { version =>
          confirmDownload0(version.id, downloadType, token)
            .mapError(_ => Redirect(ShowProject(author, slug)).withError("error.plugin.noConfirmDownload"))
        }
        .map {
          case (dl, optNewSession) =>
            val newSession = optNewSession.getOrElse(request.session)
            dl.downloadType match {
              case DownloadType.UploadedFile =>
                Redirect(self.download(author, slug, target, token)).withSession(newSession)
              case DownloadType.JarFile =>
                Redirect(self.downloadJar(author, slug, target, token)).withSession(newSession)
            }
        }
    }
  }

  /**
    * Confirms the download and prepares the unsafe download.
    */
  private def confirmDownload0(versionId: DbRef[Version], downloadType: Option[Int], optToken: Option[String])(
      implicit request: OreRequest[_]
  ): IO[Unit, (Model[UnsafeDownload], Option[mvc.Session])] = {
    val addr = InetString(StatTracker.remoteAddress)
    val dlType = downloadType
      .flatMap(DownloadType.withValueOpt)
      .getOrElse(DownloadType.UploadedFile)

    val user = request.currentUser

    val insertDownload = service.insert(
      UnsafeDownload(userId = user.map(_.id.value), address = addr, downloadType = dlType)
    )

    optToken match {
      case None =>
        val cookieKey    = DownloadWarning.cookieKey(versionId)
        val sessionIsSet = request.session.get(cookieKey).contains("set")

        if (sessionIsSet) {
          val newSession = request.session + (cookieKey -> "confirmed")
          insertDownload.tupleRight(Some(newSession))
        } else {
          IO.fail(())
        }
      case Some(token) =>
        // find warning
        ModelView
          .now(DownloadWarning)
          .find { warn =>
            (warn.address === addr) &&
            (warn.token === token) &&
            (warn.versionId === versionId) &&
            !warn.isConfirmed &&
            warn.downloadId.?.isEmpty
          }
          .value
          .get
          .flatMap { warn =>
            if (warn.hasExpired) service.delete(warn) *> IO.fail(()) else IO.succeed(warn)
          }
          .flatMap { warn =>
            // warning confirmed and redirect to download
            for {
              unsafeDownload <- insertDownload
              _              <- service.update(warn)(_.copy(isConfirmed = true, downloadId = Some(unsafeDownload.id)))
            } yield (unsafeDownload, None)
          }
    }
  }

  /**
    * Sends the specified project's current recommended version to the client.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Sent file
    */
  def downloadRecommended(author: String, slug: String, token: Option[String]): Action[AnyContent] = {
    ProjectAction(author, slug).asyncBIO { implicit request =>
      request.project
        .recommendedVersion(ModelView.now(Version))
        .sequence
        .subflatMap(identity)
        .toRight(NotFound)
        .value
        .absolve
        .flatMap(sendVersion(request.project, _, token))
    }
  }

  /**
    * Downloads the specified version as a JAR regardless of the original
    * uploaded file type.
    *
    * @param author         Project owner
    * @param slug           Project slug
    * @param versionString  Version name
    * @return               Sent file
    */
  def downloadJar(author: String, slug: String, versionString: String, token: Option[String]): Action[AnyContent] =
    ProjectAction(author, slug).asyncBIO { implicit request =>
      getVersion(request.project, versionString).flatMap(sendJar(request.project, _, token))
    }

  private def sendJar(
      project: Model[Project],
      version: Model[Version],
      token: Option[String],
      api: Boolean = false
  )(
      implicit request: ProjectRequest[_]
  ): IO[Result, Result] = {
    if (project.visibility == Visibility.SoftDelete) {
      IO.fail(NotFound)
    } else {
      checkConfirmation(version, token).flatMap { passed =>
        if (!passed) {
          IO.succeed(
            Redirect(
              self.showDownloadConfirm(
                project.ownerName,
                project.slug,
                version.name,
                Some(DownloadType.JarFile.value),
                api = Some(api),
                None
              )
            )
          )
        } else {
          val fileName = version.fileName
          val path     = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(fileName)
          project.user[Task].orDie.flatMap { projectOwner =>
            this.stats.mapK(???).versionDownloaded(version) {
              if (fileName.endsWith(".jar"))
                IO.succeed(Ok.sendPath(path))
              else {
                val pluginFile = new PluginFile(path, projectOwner)
                val jarName    = fileName.substring(0, fileName.lastIndexOf('.')) + ".jar"
                val jarPath    = env.tmp.resolve(project.ownerName).resolve(jarName)

                import zio.blocking._
                pluginFile
                  .newJarStream[ZIO[Blocking, Throwable, ?]]
                  .use { jarIn =>
                    jarIn
                      .fold(
                        e => Task.fail(new Exception(e)),
                        is => effectBlocking(copy(is, jarPath, StandardCopyOption.REPLACE_EXISTING))
                      )
                      .unit
                  }
                  .tapError(e => IO(MDCLogger.error("an error occurred while trying to send a plugin", e)))
                  .orDie
                  .const(Ok.sendPath(jarPath, onClose = () => Files.delete(jarPath)))
              }
            }
          }

        }
      }
    }

  }

  /**
    * Downloads the Project's recommended version as a JAR regardless of the
    * original uploaded file type.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Sent file
    */
  def downloadRecommendedJar(author: String, slug: String, token: Option[String]): Action[AnyContent] = {
    ProjectAction(author, slug).asyncBIO { implicit request =>
      request.project
        .recommendedVersion(ModelView.now(Version))
        .sequence
        .subflatMap(identity)
        .toRight(NotFound)
        .value
        .absolve
        .flatMap(sendJar(request.project, _, token))
    }
  }

  /**
    * Downloads the specified version as a JAR regardless of the original
    * uploaded file type.
    *
    * @param pluginId       Project unique plugin ID
    * @param versionString  Version name
    * @return               Sent file
    */
  def downloadJarById(pluginId: String, versionString: String, optToken: Option[String]): Action[AnyContent] = {
    ProjectAction(pluginId).asyncBIO { implicit request =>
      val project = request.project
      getVersion(project, versionString).flatMap { version =>
        optToken
          .map { token =>
            confirmDownload0(version.id, Some(DownloadType.JarFile.value), Some(token)).mapError(_ => notFound) *>
              sendJar(project, version, optToken, api = true)
          }
          .getOrElse(sendJar(project, version, optToken, api = true))
      }
    }
  }

  /**
    * Downloads the Project's recommended version as a JAR regardless of the
    * original uploaded file type.
    *
    * @param pluginId Project unique plugin ID
    * @return         Sent file
    */
  def downloadRecommendedJarById(pluginId: String, token: Option[String]): Action[AnyContent] = {
    ProjectAction(pluginId).asyncBIO { implicit request =>
      val data = request.data
      request.project
        .recommendedVersion(ModelView.now(Version))
        .sequence
        .subflatMap(identity)
        .toRight(NotFound)
        .value
        .absolve
        .flatMap(sendJar(data.project, _, token, api = true))
    }
  }
}
