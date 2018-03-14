package controllers.project

import java.io.InputStream
import java.nio.file.Files._
import java.nio.file.{Files, StandardCopyOption}
import java.sql.Timestamp
import java.util.{Date, UUID}
import javax.inject.Inject

import com.github.tminglei.slickpg.InetString
import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import discourse.OreDiscourseApi
import form.OreForms
import models.project._
import ore.permission.{EditVersions, ReviewProjects}
import ore.project.factory.{PendingProject, ProjectFactory}
import ore.project.io.DownloadTypes._
import ore.project.io.{DownloadTypes, InvalidPluginFileException, PluginFile, PluginUpload}
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.filters.csrf.CSRF
import security.spauth.SingleSignOnConsumer
import util.StringUtils._
import views.html.projects.{versions => views}
import _root_.views.html.helper
import ore.project.factory.TagAlias.ProjectTag
import util.JavaUtils.autoClose

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Controller for handling Version related actions.
  */
class Versions @Inject()(stats: StatTracker,
                         forms: OreForms,
                         factory: ProjectFactory,
                         forums: OreDiscourseApi,
                         implicit override val bakery: Bakery,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)
  extends OreBaseController {

  private val fileManager = this.projects.fileManager
  private val self = controllers.project.routes.Versions
  private val warnings = this.service.access[DownloadWarning](classOf[DownloadWarning])

  private def VersionEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditVersions)

  /**
    * Shows the specified version view page.
    *
    * @param author        Owner name
    * @param slug          Project slug
    * @param versionString Version name
    * @return Version view
    */
  def show(author: String, slug: String, versionString: String) = ProjectAction(author, slug).async { implicit request =>
    implicit val project = request.project
    withVersionAsync(versionString) { version =>
      version.channel.map{ channel =>
          this.stats.projectViewed { implicit request =>
           Ok(views.view(project, channel, version))
        }
      }
    }
  }

  /**
    * Saves the specified Version's description.
    *
    * @param author        Project owner
    * @param slug          Project slug
    * @param versionString Version name
    * @return View of Version
    */
  def saveDescription(author: String, slug: String, versionString: String) = {
    VersionEditAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersion(versionString) { version =>
        version.description = this.forms.VersionDescription.bindFromRequest.get.trim
        Redirect(self.show(author, slug, versionString))
      }
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
  def setRecommended(author: String, slug: String, versionString: String) = {
    VersionEditAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersion(versionString) { version =>
        project.recommendedVersion = version
        Redirect(self.show(author, slug, versionString))
      }
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
  def approve(author: String, slug: String, versionString: String) = {
    (AuthedProjectAction(author, slug, requireUnlock = true)
      andThen ProjectPermissionAction(ReviewProjects)).async { implicit request =>
      implicit val project = request.project
      withVersion(versionString) { version =>
        version.setReviewed(reviewed = true)
        version.reviewer = request.user
        version.approvedAt = this.service.theTime
        Redirect(self.show(author, slug, versionString))
      }
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
  def showList(author: String, slug: String, channels: Option[String], page: Option[Int]) = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.project
      project.channels.toSeq.flatMap { allChannels =>
        var visibleNames: Option[Array[String]] = channels.map(_.toLowerCase.split(','))
        val visible: Option[Array[Channel]] = visibleNames.map(_.map { name =>
          allChannels.find(_.name.equalsIgnoreCase(name))
        }).map(_.flatten)

        val visibleIds: Array[Int] = visible.map(_.map(_.id.get)).getOrElse(allChannels.map(_.id.get).toArray)

        val pageSize = this.config.projects.get[Int]("init-version-load")
        val p = page.getOrElse(1)
        val futureVersions = project.versions.sorted(
          ordering = _.createdAt.desc,
          filter = _.channelId inSetBind visibleIds,
          offset = pageSize * (p - 1),
          limit = pageSize)

        if (visibleNames.isDefined && visibleNames.get.toSet.equals(allChannels.map(_.name.toLowerCase).toSet)) {
          visibleNames = None
        }

        for {
          versions <- futureVersions
        } yield {
          this.stats.projectViewed { implicit request =>
            Ok(views.list(project, allChannels, versions, visibleNames, p))
          }
        }
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
  def showCreator(author: String, slug: String) = VersionEditAction(author, slug).async { implicit request =>
    val project = request.project
    for {
      channels <- project.channels.all
    } yield {
      Ok(views.create(project, None, Some(channels.toSeq), showFileControls = true))
    }
  }

  /**
    * Uploads a new version for a project for further processing.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @return Version create page (with meta)
    */
  def upload(author: String, slug: String) = VersionEditAction(author, slug).async { implicit request =>
    val call = self.showCreator(author, slug)
    val user = request.user
    this.factory.getUploadError(user) match {
      case Some(error) =>
        Future.successful(Redirect(call).withError(error))
      case None =>
        PluginUpload.bindFromRequest() match {
          case None =>
            Future.successful(Redirect(call).withError("error.noFile"))
          case Some(uploadData) =>
            try {
              this.factory.processSubsequentPluginUpload(uploadData, user, request.project).map(_.fold(
                err => Redirect(call).withError(err),
                version => {
                  version.underlying.authorId = user.id.getOrElse(-1)
                  Redirect(self.showCreatorWithMeta(request.project.ownerName, slug, version.underlying.versionString))
                }
              ))
            } catch {
              case e: InvalidPluginFileException =>
                Future.successful(Redirect(call).withError(Option(e.getMessage).getOrElse("")))
            }
        }
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
  def showCreatorWithMeta(author: String, slug: String, versionString: String) = {
    UserLock(ShowProject(author, slug)).async { implicit request =>
      // Get pending version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          Future(Redirect(self.showCreator(author, slug)))
        case Some(pendingVersion) =>
          // Get project
          pendingOrReal(author, slug) flatMap {
            case None =>
              Future.successful(Redirect(self.showCreator(author, slug)))
            case Some(p) => p match {
              case pending: PendingProject =>
                Future.successful(Ok(views.create(pending.underlying, Some(pendingVersion), None, showFileControls = false)))
              case real: Project =>
                real.channels.toSeq.map { channels =>
                  Ok(views.create(real, Some(pendingVersion), Some(channels), showFileControls = true))
                }
            }
          }
      }
    }
  }

  private def pendingOrReal(author: String, slug: String): Future[Option[Any]] = {
    // Returns either a PendingProject or existing Project
    this.projects.withSlug(author, slug) map {
      case None => this.factory.getPendingProject(author, slug)
      case Some(project) => Some(project)
    }
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
  def publish(author: String, slug: String, versionString: String) = {
    UserLock(ShowProject(author, slug)).async { implicit request =>
      // First get the pending Version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          // Not found
          Future(Redirect(self.showCreator(author, slug)))
        case Some(pendingVersion) =>
          // Get submitted channel
          this.forms.VersionCreate.bindFromRequest.fold(
            hasErrors => {
              // Invalid channel
              val call = self.showCreatorWithMeta(author, slug, versionString)
              Future(Redirect(call).withError(hasErrors.errors.head.message))
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
                  withProjectAsync(author, slug) { project =>
                    project.channels.find {
                      equalsIgnoreCase(_.name, pendingVersion.channelName)
                    } flatMap {
                      case None => versionData.addTo(project)
                      case Some(channel) => Future(Right(channel))
                    } flatMap { channelResult =>
                      channelResult.fold(
                        error => {
                          Future.successful(Redirect(self.showCreatorWithMeta(author, slug, versionString)).withError(error))
                        },
                        _ => {
                          // Update description
                          versionData.content.foreach { content =>
                            pendingVersion.underlying.description = content.trim
                          }

                          pendingVersion.complete.map { newVersion =>
                            if (versionData.recommended)
                              project.recommendedVersion = newVersion._1
                            addUnstableTag(newVersion._1, versionData.unstable)

                            Redirect(self.show(author, slug, versionString))
                          }
                        }
                      )
                    }
                  }
                case Some(pendingProject) =>
                  // Found a pending project, create it with first version
                  pendingProject.complete.map { created =>
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
  def delete(author: String, slug: String, versionString: String) = {
    VersionEditAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersion(versionString) { version =>
        this.projects.deleteVersion(version)
        Redirect(self.showList(author, slug, None, None))
      }
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
  def download(author: String, slug: String, versionString: String, token: Option[String]) = {
    ProjectAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersionAsync(versionString) { version =>
        sendVersion(project, version, token)
      }
    }
  }

  private def sendVersion(project: Project,
                          version: Version,
                          token: Option[String])
                         (implicit req: ProjectRequest[_]): Future[Result] = {
    checkConfirmation(project, version, token).map { passed =>
      if (passed)
        _sendVersion(project, version)
      else
        Redirect(self.showDownloadConfirm(
          project.ownerName, project.slug, version.name, Some(UploadedFile.id), api = Some(false)))
    }

  }

  private def checkConfirmation(project: Project,
                                version: Version,
                                token: Option[String])
                               (implicit req: ProjectRequest[_]): Future[Boolean] = {
    if (version.isReviewed)
      return Future(true)
    // check for confirmation
    req.cookies.get(DownloadWarning.COOKIE).map(_.value).orElse(token) match {
      case None =>
        // unconfirmed
        Future(false)
      case Some(tkn) =>
        this.warnings.find { warn =>
          (warn.token === tkn) &&
            (warn.versionId === version.id.get) &&
            (warn.address === InetString(StatTracker.remoteAddress)) &&
            warn.isConfirmed
        } map {
          case None => false
          case Some(warn) =>
          if (warn.hasExpired) {
            warn.remove()
            false
          } else
            true
        }
    }
  }

  private def _sendVersion(project: Project, version: Version)(implicit req: ProjectRequest[_]): Result = {
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
                          api: Option[Boolean]) = {
    ProjectAction(author, slug).async { implicit request =>
      val dlType = downloadType.flatMap(i => DownloadTypes.values.find(_.id == i)).getOrElse(DownloadTypes.UploadedFile)
      implicit val project = request.project
      withVersionAsync(target) { version =>
        if (version.isReviewed)
          Future(Redirect(ShowProject(author, slug)))
        else {
          val userAgent = request.headers.get("User-Agent")
          var curl: Boolean = false
          var wget: Boolean = false
          if (userAgent.isDefined) {
            val ua = userAgent.get.toLowerCase
            curl = ua.startsWith("curl/")
            wget = ua.startsWith("wget/")
          }

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

          if (wget) {
            Future(
            MultipleChoices(this.messagesApi("version.download.confirm.wget"))
              .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\""))
          } else if (curl) {
            Future(MultipleChoices(this.messagesApi("version.download.confirm.body.plain",
              self.confirmDownload(author, slug, target, Some(dlType.id), token).absoluteURL(),
              CSRF.getToken.get.value) + "\n")
              .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\""))
          } else if (api.getOrElse(false)) {
            Future(MultipleChoices(Json.obj(
              "message" -> this.messagesApi("version.download.confirm.body.api").split('\n'),
              "post" -> helper.CSRF(
                self.confirmDownload(author, slug, target, Some(dlType.id), token)).absoluteURL())))
          } else {
            warning.map(warn => MultipleChoices(views.unsafeDownload(project, version, dlType, token)).withCookies(warn.cookie))
          }
        }
      }
    }
  }

  def confirmDownload(author: String, slug: String, target: String, downloadType: Option[Int], token: String) = {
    ProjectAction(author, slug) async { implicit request =>
      implicit val project = request.project
      withVersionAsync(target) { version =>
        if (version.isReviewed)
          Future(Redirect(ShowProject(author, slug)))
        else {
          val addr = InetString(StatTracker.remoteAddress)
          val dlType = downloadType
            .flatMap(i => DownloadTypes.values.find(_.id == i))
            .getOrElse(DownloadTypes.UploadedFile)
          // find warning
          this.warnings.find { warn =>
            (warn.address === addr) &&
              (warn.token === token) &&
              (warn.versionId === version.id.get) &&
              !warn.isConfirmed &&
              (warn.downloadId === -1)
          } flatMap {
            case None => Future(Redirect(ShowProject(author, slug)))
            case Some(warn) =>
            if (warn.hasExpired) {
              // warning has expired
              warn.remove()
              Future(Redirect(ShowProject(author, slug)))
            } else {
              // warning confirmed and redirect to download
              warn.setConfirmed()
              // create record of download
              val downloads = this.service.access[UnsafeDownload](classOf[UnsafeDownload])
              this.users.current.flatMap { user =>
                val userId = user.flatMap(_.id)
                downloads.add(UnsafeDownload(
                  userId = userId,
                  address = addr,
                  downloadType = dlType))
              } map { dl =>
                warn.download = dl
              } map { _ =>
                dlType match {
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
            }
          }
        }
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
  def downloadRecommended(author: String, slug: String, token: Option[String]) = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.project
      project.recommendedVersion.flatMap { rv =>
        sendVersion(project, rv, token)
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
  def downloadJar(author: String, slug: String, versionString: String, token: Option[String]) = {
    ProjectAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersionAsync(versionString)(version =>
        sendJar(project, version, token))
    }
  }

  private def sendJar(project: Project,
                      version: Version,
                      token: Option[String],
                      api: Boolean = false)
                     (implicit request: ProjectRequest[_]): Future[Result] = {
    if (project.visibility == VisibilityTypes.SoftDelete) {
      return Future(notFound)
    }
    checkConfirmation(project, version, token).flatMap { passed =>
      if (!passed)
        Future.successful(Redirect(self.showDownloadConfirm(
          project.ownerName, project.slug, version.name, Some(JarFile.id), api = Some(api))))
      else {
        val fileName = version.fileName
        val path = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(fileName)
        project.owner.user.map { projectOwner =>
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
  def downloadRecommendedJar(author: String, slug: String, token: Option[String]) = {
    ProjectAction(author, slug).async { implicit request =>
      val project = request.project
      project.recommendedVersion.flatMap { rv =>
        sendJar(project, rv, token)
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
  def downloadJarById(pluginId: String, versionString: String, token: Option[String]) = {
    ProjectAction(pluginId).async { implicit request =>
      implicit val project = request.project
      withVersionAsync(versionString)(version =>
        sendJar(project, version, token, api = true))
    }
  }

  /**
    * Downloads the Project's recommended version as a JAR regardless of the
    * original uploaded file type.
    *
    * @param pluginId Project unique plugin ID
    * @return         Sent file
    */
  def downloadRecommendedJarById(pluginId: String, token: Option[String]) = {
    ProjectAction(pluginId).async { implicit request =>
      val project = request.project
      project.recommendedVersion.flatMap { rv =>
        sendJar(project, rv, token, api = true)
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
  def downloadSignature(author: String, slug: String, versionString: String) = {
    ProjectAction(author, slug).async { implicit request =>
      implicit val project = request.project
      withVersion(versionString)(sendSignatureFile(_, project))
    }
  }

  /**
    * Downloads the signature file for the specified version.
    *
    * @param pluginId       Project unique plugin ID
    * @param versionString  Version name
    * @return               Sent file
    */
  def downloadSignatureById(pluginId: String, versionString: String) = ProjectAction(pluginId).async { implicit request =>
    implicit val project = request.project
    withVersion(versionString)(sendSignatureFile(_, project))
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Sent file
    */
  def downloadRecommendedSignature(author: String, slug: String) = ProjectAction(author, slug).async { implicit request =>
    request.project.recommendedVersion.map(sendSignatureFile(_, request.project))
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param pluginId Project unique plugin ID
    * @return         Sent file
    */
  def downloadRecommendedSignatureById(pluginId: String) = ProjectAction(pluginId).async { implicit request =>
    request.project.recommendedVersion.map(sendSignatureFile(_, request.project))
  }

  private def sendSignatureFile(version: Version, project: Project)(implicit request: Request[_]): Result = {
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
