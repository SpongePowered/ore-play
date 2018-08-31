package controllers.project

import java.nio.file.Files._
import java.nio.file.{Files, StandardCopyOption}
import java.sql.Timestamp
import java.util.{Date, UUID}

import com.github.tminglei.slickpg.InetString

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import controllers.sugar.Requests.{AuthRequest, OreRequest, ProjectRequest}
import db.ModelService
import db.impl.OrePostgresDriver.api._
import discourse.OreDiscourseApi
import form.OreForms
import javax.inject.Inject

import models.project._
import models.viewhelper.{ProjectData, VersionData}
import ore.permission.{EditSettings, EditVersions, HardRemoveProject, HardRemoveVersion, ReviewProjects, UploadVersions, ViewLogs}
import ore.project.factory.TagAlias.ProjectTag
import ore.project.factory.{PendingProject, PendingVersion, ProjectFactory}
import ore.project.io.DownloadTypes._
import ore.project.io.{DownloadTypes, InvalidPluginFileException, PluginFile, PluginUpload}
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.filters.csrf.CSRF
import security.spauth.SingleSignOnConsumer
import util.StringUtils._
import util.syntax._
import views.html.projects.{versions => views}
import _root_.views.html.helper
import models.user.{LoggedAction, UserActionLogger}
import ore.project.factory.TagAlias.ProjectTag
import util.JavaUtils.autoClose
import scala.concurrent.{ExecutionContext, Future}

import util.functional.{EitherT, OptionT}
import util.instances.future._

import db.impl.VersionTable

/**
  * Controller for handling Version related actions.
  */
class Versions @Inject()(stats: StatTracker,
                         forms: OreForms,
                         factory: ProjectFactory,
                         forums: OreDiscourseApi,
                         implicit override val bakery: Bakery,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit override val cache: AsyncCacheApi,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)(implicit val ec: ExecutionContext)
  extends OreBaseController {

  private val fileManager = this.projects.fileManager
  private val self = controllers.project.routes.Versions
  private val warnings = this.service.access[DownloadWarning](classOf[DownloadWarning])

  private def VersionEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditVersions)

  private def VersionUploadAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(UploadVersions)

  /**
    * Shows the specified version view page.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Version view
    */
  def show(author: String, slug: String, versionString: String): Action[AnyContent] = ProjectAction(author, slug) async { request =>
    implicit val r: OreRequest[AnyContent] = request.request
    val res = for {
      version <- getVersion(request.data.project, versionString)
      data <- EitherT.right[Result](VersionData.of(request, version))
      response <- EitherT.right[Result](this.stats.projectViewed(request)(request => Ok(views.view(data, request.scoped))))
    } yield response

    res.merge
  }

  /**
    * Saves the specified Version's description.
    *
    * @param author        Project owner
    * @param slug          Project slug
    * @param versionString Version name
    * @return View of Version
    */
  def saveDescription(author: String, slug: String, versionString: String): Action[AnyContent] = {
    VersionEditAction(author, slug).async { request =>
      implicit val r: Requests.AuthRequest[AnyContent] = request.request
      val res = for {
        version <- getVersion(request.data.project, versionString)
        description <- bindFormEitherT[Future](this.forms.VersionDescription)(_ => BadRequest: Result)
      } yield {
        val oldDescription = version.description.getOrElse("")
        val newDescription = description.trim
        version.setDescription(newDescription)
        UserActionLogger.log(request.request, LoggedAction.VersionDescriptionEdited, version.id.getOrElse(-1), newDescription, oldDescription)
        Redirect(self.show(author, slug, versionString))
      }

      res.merge
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
    VersionEditAction(author, slug).async { implicit request =>
      implicit val r: Requests.AuthRequest[AnyContent] = request.request
      getVersion(request.data.project, versionString).map { version =>
        request.data.project.setRecommendedVersion(version)
        UserActionLogger.log(request.request, LoggedAction.VersionAsRecommended, version.id.getOrElse(-1), "recommended version", "listed version")
        Redirect(self.show(author, slug, versionString))
      }.merge
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
  def approve(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (AuthedProjectAction(author, slug, requireUnlock = true)
      andThen ProjectPermissionAction(ReviewProjects)).async { implicit request =>
      implicit val r: Requests.AuthRequest[AnyContent] = request.request
      getVersion(request.data.project, versionString).map { version =>
        version.setReviewed(reviewed = true)
        version.setReviewer(request.user)
        version.setApprovedAt(this.service.theTime)
        UserActionLogger.log(request.request, LoggedAction.VersionApproved, version.id.getOrElse(-1), "approved", "unapproved")
        Redirect(self.show(author, slug, versionString))
      }.merge
    }
  }

  /**
    * Displays the "versions" tab within a Project view.
    *
    * @param author   Owner of project
    * @param slug     Project slug
    * @param channels Visible channels
    * @return View of project
    */
  def showList(author: String, slug: String, channels: Option[String]): Action[AnyContent] = {
    ProjectAction(author, slug).async { request =>
      val data = request.data
      implicit val r: OreRequest[AnyContent] = request.request

      data.project.channels.toSeq.flatMap { allChannels =>
        val visibleNames = channels.fold(allChannels.map(_.name.toLowerCase))(_.toLowerCase.split(',').toSeq)
        val visible = allChannels.filter(ch => visibleNames.contains(ch.name.toLowerCase))
        val visibleIds = visible.map(_.id.get)

        def versionFilter(v: VersionTable): Rep[Boolean] = {
          val inChannel = v.channelId inSetBind visibleIds
          val isVisible =
            if(r.data.globalPerm(ReviewProjects)) true: Rep[Boolean]
            else v.visibility === VisibilityTypes.Public
          inChannel && isVisible
        }

        val futureVersionCount = data.project.versions.count(versionFilter)

        val visibleNamesForView = if(visibleNames == allChannels.map(_.name.toLowerCase)) Nil else visibleNames

        for {
          versionCount <- futureVersionCount
          r <- this.stats.projectViewed(request) { request =>
            Ok(views.list(data, request.scoped, allChannels, versionCount, visibleNamesForView))
          }
        } yield r
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
  def showCreator(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).async { request =>
    val data = request.data
    implicit val r: Requests.AuthRequest[AnyContent] = request.request
    data.project.channels.all.map { channels =>
      Ok(views.create(data, data.settings.forumSync, None, Some(channels.toSeq), showFileControls = true))
    }
  }

  /**
    * Uploads a new version for a project for further processing.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @return Version create page (with meta)
    */
  def upload(author: String, slug: String): Action[AnyContent] = VersionUploadAction(author, slug).async { implicit request =>
    val call = self.showCreator(author, slug)
    val user = request.user

    val uploadData = this.factory.getUploadError(user)
      .map(error => Redirect(call).withError(error))
      .toLeft(())
      .flatMap(_ => PluginUpload.bindFromRequest().toRight(Redirect(call).withError("error.noFile")))

    EitherT.fromEither[Future](uploadData).flatMap { data =>
      //TODO: We should get rid of this try
      try {
        this.factory
          .processSubsequentPluginUpload(data, user, request.data.project)
          .leftMap(err => Redirect(call).withError(err))
      } catch {
        case e: InvalidPluginFileException =>
          EitherT.leftT[Future, PendingVersion](Redirect(call).withErrors(Option(e.getMessage).toList))
      }
    }.map { pendingVersion =>
      pendingVersion.underlying.setAuthorId(user.id.getOrElse(-1))
      Redirect(self.showCreatorWithMeta(request.data.project.ownerName, slug, pendingVersion.underlying.versionString))
    }.merge
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
    UserLock(ShowProject(author, slug)).async { implicit request =>
      val success = OptionT.fromOption[Future](this.factory.getPendingVersion(author, slug, versionString))
        // Get pending version
        .flatMap(pendingVersion => pendingOrReal(author, slug).map(pendingVersion -> _))
        .semiFlatMap {
          case (pendingVersion, Left(pending)) =>
            Future.successful((None, ProjectData.of(request, pending), pendingVersion))
          case (pendingVersion, Right(real)) =>
            (real.channels.toSeq, ProjectData.of(real))
              .parMapN((channels, data) => (Some(channels), data, pendingVersion))
        }
        .map { case (channels, data, pendingVersion) =>
          Ok(views.create(data, data.settings.forumSync, Some(pendingVersion), channels, showFileControls = channels.isDefined))
        }

      success.getOrElse(Redirect(self.showCreator(author, slug)).withError("error.plugin.timeout"))
    }

  private def pendingOrReal(author: String, slug: String): OptionT[Future, Either[PendingProject, Project]] = {
    // Returns either a PendingProject or existing Project
    this.projects.withSlug(author, slug)
      .map[Either[PendingProject, Project]](Right.apply)
      .orElse(OptionT.fromOption[Future](this.factory.getPendingProject(author, slug)).map(Left.apply))
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
    UserLock(ShowProject(author, slug)).async { implicit request =>
      // First get the pending Version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          // Not found
          Future.successful(Redirect(self.showCreator(author, slug)).withError("error.plugin.timeout"))
        case Some(pendingVersion) =>
          // Get submitted channel
          this.forms.VersionCreate.bindFromRequest.fold(
            hasErrors => {
              // Invalid channel
              val call = self.showCreatorWithMeta(author, slug, versionString)
              Future.successful(Redirect(call).withErrors(hasErrors.errors.flatMap(_.messages)))
            },

            versionData => {
              // Channel is valid

              pendingVersion.channelName = versionData.channelName.trim
              pendingVersion.channelColor = versionData.color
              pendingVersion.createForumPost = versionData.forumPost

              // Check for pending project
              this.factory.getPendingProject(author, slug) match {
                case None =>
                  // No pending project, create version for existing project
                  getProject(author, slug).flatMap { project =>
                    project.channels
                      .find(equalsIgnoreCase(_.name, pendingVersion.channelName))
                      .toRight(versionData.addTo(project))
                      .leftFlatMap(identity)
                      .semiFlatMap { _ =>
                        // Update description
                        versionData.content.foreach { content =>
                          pendingVersion.underlying.setDescription(content.trim)
                        }

                        pendingVersion.complete.map { newVersion =>
                          if (versionData.recommended)
                            project.setRecommendedVersion(newVersion._1)
                          addUnstableTag(newVersion._1, versionData.unstable)
                          UserActionLogger.log(request, LoggedAction.VersionUploaded, newVersion._1.id.getOrElse(-1), "published", "null")
                          Redirect(self.show(author, slug, versionString))
                        }
                      }
                      .leftMap(error => Redirect(self.showCreatorWithMeta(author, slug, versionString)).withError(error))
                  }.merge
                case Some(pendingProject) =>
                  // Found a pending project, create it with first version
                  pendingProject.complete.map { created =>
                    UserActionLogger.log(request, LoggedAction.ProjectCreated, created._1.id.getOrElse(-1), "created", "null")
                    addUnstableTag(created._2, versionData.unstable)
                    Redirect(ShowProject(author, slug))
                  }
              }
            }
          )
      }
    }
  }

  private def addUnstableTag(version: Version, unstable: Boolean) = {
    if (unstable) {
      service.access(classOf[ProjectTag])
        .filter(t => t.name === "Unstable" && t.data === "").map { tagsWithVersion =>
        if (tagsWithVersion.isEmpty) {
          val tag = Tag(
            _versionIds = List(version.id.get),
            name = "Unstable",
            data = "",
            color = TagColors.Unstable
          )
          service.access(classOf[ProjectTag]).add(tag).flatMap { tag =>
            // requery the tag because it now includes the id
            service.access(classOf[ProjectTag]).filter(t => t.name === tag.name && t.data === tag.data).map(_.toList.head)
          } map { newTag =>
            version.addTag(newTag)
          }
        } else {
          val tag = tagsWithVersion.head
          tag.addVersionId(version.id.get)
          version.addTag(tag)
        }
      }
    }
  }

  /**
    * Deletes the specified version and returns to the version page.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Versions page
    */
  def delete(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](HardRemoveVersion)).async { implicit request =>
      implicit val r: Request[AnyContent] = request.request
      getProjectVersion(author, slug, versionString).map { version =>
        this.projects.deleteVersion(version)
        UserActionLogger.log(request, LoggedAction.VersionDeleted, version.id.getOrElse(-1), "null", "")
        Redirect(self.showList(author, slug, None))
      }.merge
    }
  }

  /**
    * Soft deletes the specified version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def softDelete(author: String, slug: String, versionString: String): Action[AnyContent] = VersionEditAction(author, slug).async { request =>
    implicit val oreRequest: AuthRequest[AnyContent] = request.request
    val project: Project = request.data.project
    val res = for {
      comment <- bindFormEitherT[Future](this.forms.NeedsChanges)(_ => BadRequest)
      version <- getVersion(project, versionString)
      _ <- EitherT.right[Result](this.projects.prepareDeleteVersion(version))
      _ <- EitherT.right[Result](version.setVisibility(VisibilityTypes.SoftDelete, comment, request.user.id.get))
    } yield Redirect(self.showList(author, slug, None))

    res.merge
  }

  def showLog(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ViewLogs)) andThen ProjectAction(author, slug) async { request =>
      implicit val r: OreRequest[AnyContent] = request.request
      implicit val project: Project = request.data.project
      val res = for {
        version <- getVersion(project, versionString)
        changes <- EitherT.right[Result](version.visibilityChangesByDate)
        changedBy <- EitherT.right[Result](Future.sequence(changes.map(_.created.value)))
      } yield {
        val visChanges = changes zip changedBy
        Ok(views.log(project, version, visChanges))
      }

      res.merge
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
  def download(author: String, slug: String, versionString: String, token: Option[String]): Action[AnyContent] = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.data.project
      implicit val r: OreRequest[AnyContent] = request.request
      getVersion(project, versionString).semiFlatMap { version =>
        sendVersion(project, version, token)
      }.merge
    }
  }

  private def sendVersion(project: Project,
                          version: Version,
                          token: Option[String])
                         (implicit req: ProjectRequest[_]): Future[Result] = {
    checkConfirmation(project, version, token).flatMap { passed =>
      if (passed)
        _sendVersion(project, version)
      else
        Future.successful(
          Redirect(self.showDownloadConfirm(
            project.ownerName, project.slug, version.name, Some(UploadedFile.id), api = Some(false))))
    }
  }

  private def checkConfirmation(project: Project,
                                version: Version,
                                token: Option[String])
                               (implicit req: ProjectRequest[_]): Future[Boolean] = {
    if (version.isReviewed)
      return Future.successful(true)

    // check for confirmation
    OptionT
      .fromOption[Future](req.cookies.get(DownloadWarning.COOKIE).map(_.value).orElse(token))
      .flatMap { tkn =>
        this.warnings.find { warn =>
          (warn.token === tkn) &&
            (warn.versionId === version.id.get) &&
            (warn.address === InetString(StatTracker.remoteAddress)) &&
            warn.isConfirmed
        }
      }.exists { warn =>
        if (!warn.hasExpired) true
        else {
          warn.remove()
          false
        }
      }
  }

  private def _sendVersion(project: Project, version: Version)(implicit req: ProjectRequest[_]): Future[Result] = {
    this.stats.versionDownloaded(version) { implicit request =>
      Ok.sendPath(this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
        .resolve(version.fileName))
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
    * @return       Confirmation view
    */
  def showDownloadConfirm(author: String,
                          slug: String,
                          target: String,
                          downloadType: Option[Int],
                          api: Option[Boolean]): Action[AnyContent] = {
    ProjectAction(author, slug).async { request =>
      val dlType = downloadType.flatMap(i => DownloadTypes.values.find(_.id == i)).getOrElse(DownloadTypes.UploadedFile)
      implicit val r: OreRequest[AnyContent] = request.request
      implicit val lang: Lang = request.lang
      val project = request.data.project
      getVersion(project, target)
        .filterOrElse(v => !v.isReviewed, Redirect(ShowProject(author, slug)).withError("error.plugin.stateChanged"))
        .semiFlatMap { version =>
          // generate a unique "warning" object to ensure the user has landed
          // on the warning before downloading
          val token = UUID.randomUUID().toString
          val expiration = new Timestamp(new Date().getTime + this.config.security.get[Long]("unsafeDownload.maxAge"))
          val address = InetString(StatTracker.remoteAddress)
          // remove old warning attached to address
          this.warnings.removeAll(_.address === address)
          // create warning
          val warning = this.warnings.add(DownloadWarning(
            expiration = expiration,
            token = token,
            versionId = version.id.get,
            address = InetString(StatTracker.remoteAddress)))

          if (api.getOrElse(false)) {
            warning.map { warn =>
              MultipleChoices(Json.obj(
                "message" -> this.messagesApi("version.download.confirm.body.api").split('\n'),
                "post" ->  self.confirmDownload(author, slug, target, Some(dlType.id), token).absoluteURL(),
                "url" -> self.downloadJarById(project.pluginId, version.name, Some(token)).absoluteURL(),
                "token" -> token)
              )
            }
          } else {
            val userAgent = request.headers.get("User-Agent")
            var curl = false
            var wget = false
            userAgent.map(_.toLowerCase).foreach { ua =>
              curl = ua.startsWith("curl/")
              wget = ua.startsWith("wget/")
            }

            if (wget) {
              Future.successful(
                MultipleChoices(this.messagesApi("version.download.confirm.wget"))
                  .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\""))
            } else if (curl) {
              Future.successful(MultipleChoices(this.messagesApi("version.download.confirm.body.plain",
                self.confirmDownload(author, slug, target, Some(dlType.id), token).absoluteURL(),
                CSRF.getToken.get.value) + "\n")
                .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\""))
            } else {
              (warning, version.channel.map(_.isNonReviewed)).parMapN { (warn, nonReviewed) =>
                MultipleChoices(views.unsafeDownload(project, version, nonReviewed, dlType, token))
                  .withCookies(warn.cookie)
              }
            }
          }
        }.merge
    }
  }

  def confirmDownload(author: String, slug: String, target: String, downloadType: Option[Int], token: String): Action[AnyContent] = {
    ProjectAction(author, slug) async { request =>
      implicit val r: OreRequest[_] = request.request
      getVersion(request.data.project, target)
        .filterOrElse(v => !v.isReviewed, Redirect(ShowProject(author, slug)).withError("error.plugin.stateChanged"))
        .flatMap(version => confirmDownload0(version.id.get, downloadType, token).toRight(Redirect(ShowProject(author, slug)).withError("error.plugin.noConfirmDownload")))
        .map { dl =>
          dl.downloadType match {
            case UploadedFile =>
              Redirect(self.download(author, slug, target, Some(token)))
            case JarFile =>
              Redirect(self.downloadJar(author, slug, target, Some(token)))
            case SignatureFile =>
              // Note: Shouldn't get here in the first place since sig files
              // don't need confirmation, but added as a failsafe.
              Redirect(self.downloadSignature(author, slug, target))
            case _ =>
              throw new Exception("unknown download type: " + downloadType)
          }
        }
        .merge
    }
  }

  /**
    * Confirms the download and prepares the unsafe download.
    */
  private def confirmDownload0(versionId: Int, downloadType: Option[Int],token: String)(implicit requestHeader: Request[_]): OptionT[Future, UnsafeDownload] = {
    val addr = InetString(StatTracker.remoteAddress)
    val dlType = downloadType
      .flatMap(i => DownloadTypes.values.find(_.id == i))
      .getOrElse(DownloadTypes.UploadedFile)
    // find warning
    this.warnings.find { warn =>
      (warn.address === addr) &&
        (warn.token === token) &&
        (warn.versionId === versionId) &&
        !warn.isConfirmed &&
        (warn.downloadId === -1)
    }.filterNot { warn =>
      val isInvalid = warn.hasExpired
      // warning has expired
      if(isInvalid) warn.remove()

      isInvalid
    }.semiFlatMap { warn =>
      // warning confirmed and redirect to download
      val downloads = this.service.access[UnsafeDownload](classOf[UnsafeDownload])
      for {
        user <- this.users.current.value
        _ <- warn.setConfirmed()
        unsafeDownload <- downloads.add(UnsafeDownload(
          userId = user.flatMap(_.id),
          address = addr,
          downloadType = dlType))
        _ <- warn.setDownload(unsafeDownload)
      } yield unsafeDownload
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
    ProjectAction(author, slug).async { implicit request =>
      val data = request.data
      data.project.recommendedVersion.flatMap { rv =>
        sendVersion(data.project, rv, token)
      }
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
  def downloadJar(author: String, slug: String, versionString: String, token: Option[String]): Action[AnyContent] = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.data.project
      implicit val r: OreRequest[AnyContent] = request.request
      getVersion(project, versionString).semiFlatMap(version => sendJar(project, version, token)).merge
    }
  }

  private def sendJar(project: Project,
                      version: Version,
                      token: Option[String],
                      api: Boolean = false)
                     (implicit request: ProjectRequest[_]): Future[Result] = {
    if (project.visibility == VisibilityTypes.SoftDelete) {
      return Future.successful(NotFound)
    }
    checkConfirmation(project, version, token).flatMap { passed =>
      if (!passed)
        Future.successful(Redirect(self.showDownloadConfirm(
          project.ownerName, project.slug, version.name, Some(JarFile.id), api = Some(api))))
      else {
        val fileName = version.fileName
        val path = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(fileName)
        project.owner.user.flatMap { projectOwner =>
          this.stats.versionDownloaded(version) { implicit request =>
            if (fileName.endsWith(".jar"))
              Ok.sendPath(path)
            else {
              val pluginFile = new PluginFile(path, signaturePath = null, projectOwner)
              val jarName = fileName.substring(0, fileName.lastIndexOf('.')) + ".jar"
              val jarPath = this.fileManager.env.tmp.resolve(project.ownerName).resolve(jarName)

              autoClose(pluginFile.newJarStream) { jarIn =>
                copy(jarIn, jarPath, StandardCopyOption.REPLACE_EXISTING)
              }{ e =>
                Logger.error("an error occurred while trying to send a plugin", e)
              }

              Ok.sendPath(jarPath, onClose = () => Files.delete(jarPath))
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
    ProjectAction(author, slug).async { implicit request =>
      val data = request.data
      data.project.recommendedVersion.flatMap { rv =>
        sendJar(data.project, rv, token)
      }
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
    ProjectAction(pluginId).async { implicit request =>
      val project = request.data.project
      implicit val r: OreRequest[AnyContent] = request.request
      getVersion(project, versionString).semiFlatMap { version =>
        optToken.map { token =>
          confirmDownload0(version.id.get, Some(JarFile.id), token)(request.request).value.flatMap { _ =>
            sendJar(project, version, optToken, api = true)
          }
        }.getOrElse(sendJar(project, version, optToken, api = true))
      }.merge
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
    ProjectAction(pluginId).async { implicit request =>
      val data = request.data
      data.project.recommendedVersion.flatMap { rv =>
        sendJar(data.project, rv, token, api = true)
      }
    }
  }

  /**
    * Sends the specified Project Version signature file to the client.
    *
    * @param author         Project owner
    * @param slug           Project slug
    * @param versionString  Version string
    * @return               Sent file
    */
  def downloadSignature(author: String, slug: String, versionString: String): Action[AnyContent] = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.data.project
      implicit val r: OreRequest[AnyContent] = request.request
      getVersion(project, versionString).map(sendSignatureFile(_, project)).merge
    }
  }

  /**
    * Downloads the signature file for the specified version.
    *
    * @param pluginId       Project unique plugin ID
    * @param versionString  Version name
    * @return               Sent file
    */
  def downloadSignatureById(pluginId: String, versionString: String): Action[AnyContent] = ProjectAction(pluginId).async { implicit request =>
    val project = request.data.project
    implicit val r: OreRequest[AnyContent] = request.request
    getVersion(project, versionString).map(sendSignatureFile(_, project)).merge
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Sent file
    */
  def downloadRecommendedSignature(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) async { implicit request =>
    implicit val data: ProjectData = request.data
    implicit val r: OreRequest[AnyContent] = request.request
    request.data.project.recommendedVersion.map(sendSignatureFile(_, request.data.project))
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param pluginId Project unique plugin ID
    * @return         Sent file
    */
  def downloadRecommendedSignatureById(pluginId: String): Action[AnyContent] = ProjectAction(pluginId).async { implicit request =>
    implicit val r: OreRequest[AnyContent] = request.request
    request.data.project.recommendedVersion.map(sendSignatureFile(_, request.data.project))
  }

  private def sendSignatureFile(version: Version, project: Project)(implicit request: OreRequest[_]): Result = {
    if (project.visibility == VisibilityTypes.SoftDelete) {
      notFound
    } else {
      val path = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(version.signatureFileName)
      if (notExists(path)) {
        Logger.warn("project version missing signature file")
        notFound
      } else
        Ok.sendPath(path)
    }
  }

}
