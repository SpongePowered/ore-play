package controllers.project

import java.io.InputStream
import java.nio.file.Files._
import java.nio.file.{Files, StandardCopyOption}
import java.sql.Timestamp
import java.util.{Date, UUID}
import javax.inject.Inject

import com.github.tminglei.slickpg.InetString
import controllers.BaseController
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
  extends BaseController {

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
  def show(author: String, slug: String, versionString: String) = ProjectAction(author, slug) { implicit request =>
    implicit val project = request.project
    withVersion(versionString) { version =>
      this.stats.projectViewed { implicit request =>
        Ok(views.view(project, version.channel, version))
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
    VersionEditAction(author, slug) { implicit request =>
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
    VersionEditAction(author, slug) { implicit request =>
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
      andThen ProjectPermissionAction(ReviewProjects)) { implicit request =>
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
    ProjectAction(author, slug) { implicit request =>
      val project = request.project
      val allChannels = project.channels.toSeq

      var visibleNames: Option[Array[String]] = channels.map(_.toLowerCase.split(','))
      val visible: Option[Array[Channel]] = visibleNames.map(_.map { name =>
        allChannels.find(_.name.equalsIgnoreCase(name))
      }).map(_.flatten)

      val visibleIds: Array[Int] = visible.map(_.map(_.id.get)).getOrElse(allChannels.map(_.id.get).toArray)

      val pageSize = this.config.projects.getInt("init-version-load").get
      val p = page.getOrElse(1)
      val versions = project.versions.sorted(
        ordering = _.createdAt.desc,
        filter = _.channelId inSetBind visibleIds,
        offset = pageSize * (p - 1),
        limit = pageSize)

      if (visibleNames.isDefined && visibleNames.get.toSet.equals(allChannels.map(_.name.toLowerCase).toSet)) {
        visibleNames = None
      }

      this.stats.projectViewed { implicit request =>
        Ok(views.list(project, allChannels, versions, visibleNames, p))
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
  def showCreator(author: String, slug: String) = VersionEditAction(author, slug) { implicit request =>
    val project = request.project
    Ok(views.create(project, None, Some(project.channels.all.toSeq), showFileControls = true))
  }

  /**
    * Uploads a new version for a project for further processing.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @return Version create page (with meta)
    */
  def upload(author: String, slug: String) = VersionEditAction(author, slug) { implicit request =>
    val call = self.showCreator(author, slug)
    val user = request.user
    this.factory.getUploadError(user) match {
      case Some(error) =>
        Redirect(call).withError(error)
      case None =>
        PluginUpload.bindFromRequest() match {
          case None =>
            Redirect(call).withError("error.noFile")
          case Some(uploadData) =>
            try {
              this.factory.processSubsequentPluginUpload(uploadData, user, request.project).fold(
                err => Redirect(call).withError(err),
                version => {
                  version.underlying.authorId = user.id.getOrElse(-1)
                  Redirect(self.showCreatorWithMeta(request.project.ownerName, slug, version.underlying.versionString))
                }
              )
            } catch {
              case e: InvalidPluginFileException =>
                Redirect(call).withError(Option(e.getMessage).getOrElse(""))
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
    UserLock(ShowProject(author, slug)) { implicit request =>
      // Get pending version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          Redirect(self.showCreator(author, slug))
        case Some(pendingVersion) =>
          // Get project
          pendingOrReal(author, slug) match {
            case None =>
              Redirect(self.showCreator(author, slug))
            case Some(p) => p match {
              case pending: PendingProject =>
                Ok(views.create(pending.underlying, Some(pendingVersion), None, showFileControls = false))
              case real: Project =>
                Ok(views.create(real, Some(pendingVersion), Some(real.channels.toSeq), showFileControls = true))
            }
          }
      }
    }
  }

  private def pendingOrReal(author: String, slug: String): Option[Any] = {
    // Returns either a PendingProject or existing Project
    this.projects.withSlug(author, slug) match {
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
    UserLock(ShowProject(author, slug)) { implicit request =>
      // First get the pending Version
      this.factory.getPendingVersion(author, slug, versionString) match {
        case None =>
          // Not found
          Redirect(self.showCreator(author, slug))
        case Some(pendingVersion) =>
          // Get submitted channel
          this.forms.VersionCreate.bindFromRequest.fold(
            hasErrors => {
              // Invalid channel
              val call = self.showCreatorWithMeta(author, slug, versionString)
              Redirect(call).withError(hasErrors.errors.head.message)
            },

            versionData => {
              // Channel is valid

              def addUnstableTag(version: Version) = {
                if (versionData.unstable) {
                  val tagsWithVersion = service.access(classOf[ProjectTag])
                    .filter(t => t.name === "Unstable" && t.data === "").toList

                  if (tagsWithVersion.isEmpty) {
                    val tag = Tag(
                      _versionIds = List(version.id.get),
                      name = "Unstable",
                      data = "",
                      color = TagColors.Unstable
                    )
                    service.access(classOf[ProjectTag]).add(tag)
                    // requery the tag because it now includes the id
                    val newTag = service.access(classOf[ProjectTag]).filter(t => t.name === tag.name && t.data === tag.data).toList.head
                    version.addTag(newTag)
                  } else {
                    val tag = tagsWithVersion.head
                    tag.addVersionId(version.id.get)
                    version.addTag(tag)
                  }
                }
              }

              pendingVersion.channelName = versionData.channelName.trim
              pendingVersion.channelColor = versionData.color

              // Check for pending project
              this.factory.getPendingProject(author, slug) match {
                case None =>
                  // No pending project, create version for existing project
                  withProject(author, slug) { project =>
                    val existingChannel = project.channels.find {
                      equalsIgnoreCase(_.name, pendingVersion.channelName)
                    }.orNull

                    var channelResult: Either[String, Channel] = Right(existingChannel)
                    if (existingChannel == null)
                      channelResult = versionData.addTo(project)

                    channelResult.fold(
                      error => {
                        Redirect(self.showCreatorWithMeta(author, slug, versionString)).withError(error)
                      },
                      channel => {
                        val newVersion = pendingVersion.complete().get
                        if (versionData.recommended)
                          project.recommendedVersion = newVersion

                        // Create forum topic reply / update description
                        versionData.content.foreach { content =>
                          val c = content.trim
                          newVersion.description = c
                          if (project.topicId != -1)
                            this.forums.postVersionRelease(project, newVersion, c)
                        }

                        addUnstableTag(newVersion)
                        Redirect(self.show(author, slug, versionString))
                      }
                    )
                  }
                case Some(pendingProject) =>
                  // Found a pending project, create it with first version
                  val project = pendingProject.complete().get
                  addUnstableTag(project.recommendedVersion)
                  Redirect(ShowProject(author, slug))
              }
            }
          )
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
    VersionEditAction(author, slug) { implicit request =>
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
    ProjectAction(author, slug) { implicit request =>
      implicit val project = request.project
      withVersion(versionString) { version =>
        sendVersion(project, version, token)
      }
    }
  }

  private def sendVersion(project: Project,
                          version: Version,
                          token: Option[String])
                         (implicit req: ProjectRequest[_]): Result = {
    if (!checkConfirmation(project, version, token))
      Redirect(self.showDownloadConfirm(
        project.ownerName, project.slug, version.name, Some(UploadedFile.id), api = Some(false)))
    else
      _sendVersion(project, version)
  }

  private def checkConfirmation(project: Project,
                                version: Version,
                                token: Option[String])
                               (implicit req: ProjectRequest[_]): Boolean = {
    if (version.isReviewed)
      return true
    // check for confirmation
    req.cookies.get(DownloadWarning.COOKIE).map(_.value).orElse(token) match {
      case None =>
        // unconfirmed
        false
      case Some(tkn) =>
        this.warnings.find { warn =>
          (warn.token === tkn) &&
            (warn.versionId === version.id.get) &&
            (warn.address === InetString(StatTracker.remoteAddress)) &&
            warn.isConfirmed
        } map { warn =>
          if (warn.hasExpired) {
            warn.remove()
            false
          } else
            true
        } getOrElse {
          false
        }
    }
  }

  private def _sendVersion(project: Project, version: Version)(implicit req: ProjectRequest[_]): Result = {
    this.stats.versionDownloaded(version) { implicit request =>
      Ok.sendFile(this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
        .resolve(version.fileName).toFile)
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
    ProjectAction(author, slug) { implicit request =>
      val dlType = downloadType.flatMap(i => DownloadTypes.values.find(_.id == i)).getOrElse(DownloadTypes.UploadedFile)
      implicit val project = request.project
      withVersion(target) { version =>
        if (version.isReviewed)
          Redirect(ShowProject(author, slug))
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
          val expiration = new Timestamp(new Date().getTime + this.config.security.getLong("unsafeDownload.maxAge").get)
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
            MultipleChoices(this.messagesApi("version.download.confirm.wget"))
              .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\"")
          } else if (curl) {
            MultipleChoices(this.messagesApi("version.download.confirm.body.plain",
              self.confirmDownload(author, slug, target, Some(dlType.id), token).absoluteURL(),
              CSRF.getToken.get.value) + "\n")
              .withHeaders("Content-Disposition" -> "inline; filename=\"README.txt\"")
          } else if (api.getOrElse(false)) {
            MultipleChoices(Json.obj(
              "message" -> this.messagesApi("version.download.confirm.body.api").split('\n'),
              "post" -> helper.CSRF(
                self.confirmDownload(author, slug, target, Some(dlType.id), token)).absoluteURL()))
          } else {
            MultipleChoices(views.unsafeDownload(project, version, dlType, token)).withCookies(warning.cookie)
          }
        }
      }
    }
  }

  def confirmDownload(author: String, slug: String, target: String, downloadType: Option[Int], token: String) = {
    ProjectAction(author, slug) { implicit request =>
      implicit val project = request.project
      withVersion(target) { version =>
        if (version.isReviewed)
          Redirect(ShowProject(author, slug))
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
          } map { warn =>
            if (warn.hasExpired) {
              // warning has expired
              warn.remove()
              Redirect(ShowProject(author, slug))
            } else {
              // warning confirmed and redirect to download
              warn.setConfirmed()
              // create record of download
              val downloads = this.service.access[UnsafeDownload](classOf[UnsafeDownload])
              val userId = this.users.current.flatMap(_.id)
              val download = downloads.add(UnsafeDownload(
                userId = userId,
                address = addr,
                downloadType = dlType))
              warn.download = download
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
          } getOrElse {
            Redirect(ShowProject(author, slug))
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
    ProjectAction(author, slug) { implicit request =>
      val project = request.project
      val rv = project.recommendedVersion
      sendVersion(project, rv, token)
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
    ProjectAction(author, slug) { implicit request =>
      implicit val project = request.project
      withVersion(versionString)(version => sendJar(project, version, token))
    }
  }

  private def sendJar(project: Project,
                      version: Version,
                      token: Option[String],
                      api: Boolean = false)
                     (implicit request: ProjectRequest[_]): Result = {
    if (project.visibility == VisibilityTypes.SoftDelete) {
      return notFound
    }
    if (!checkConfirmation(project, version, token))
      Redirect(self.showDownloadConfirm(
        project.ownerName, project.slug, version.name, Some(JarFile.id), api = Some(api)))
    else {
      val fileName = version.fileName
      val path = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(fileName)
      this.stats.versionDownloaded(version) { implicit request =>
        if (fileName.endsWith(".jar"))
          Ok.sendFile(path.toFile)
        else {
          val pluginFile = new PluginFile(path, signaturePath = null, project.owner.user)
          val jarName = fileName.substring(0, fileName.lastIndexOf('.')) + ".jar"
          val jarPath = this.fileManager.env.tmp.resolve(project.ownerName).resolve(jarName)
          var jarIn: InputStream = null
          try {
            jarIn = pluginFile.newJarStream
            copy(jarIn, jarPath, StandardCopyOption.REPLACE_EXISTING)
          } catch {
            case e: Exception =>
              Logger.error("an error occurred while trying to send a plugin", e)
          } finally {
            if (jarIn != null)
              jarIn.close()
            else
              Logger.error("could not obtain input stream for download request")
          }
          Ok.sendFile(jarPath.toFile, onClose = () => Files.delete(jarPath))
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
    ProjectAction(author, slug) { implicit request =>
      val project = request.project
      sendJar(project, project.recommendedVersion, token)
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
    ProjectAction(pluginId) { implicit request =>
      implicit val project = request.project
      withVersion(versionString)(version => sendJar(project, version, token, api = true))
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
    ProjectAction(pluginId) { implicit request =>
      val project = request.project
      sendJar(project, project.recommendedVersion, token, api = true)
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
    ProjectAction(author, slug) { implicit request =>
      implicit val project = request.project
      withVersion(versionString)(sendSignatureFile)
    }
  }

  /**
    * Downloads the signature file for the specified version.
    *
    * @param pluginId       Project unique plugin ID
    * @param versionString  Version name
    * @return               Sent file
    */
  def downloadSignatureById(pluginId: String, versionString: String) = ProjectAction(pluginId) { implicit request =>
    implicit val project = request.project
    withVersion(versionString)(sendSignatureFile)
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Sent file
    */
  def downloadRecommendedSignature(author: String, slug: String) = ProjectAction(author, slug) { implicit request =>
    sendSignatureFile(request.project.recommendedVersion)
  }

  /**
    * Downloads the signature file for the Project's recommended version.
    *
    * @param pluginId Project unique plugin ID
    * @return         Sent file
    */
  def downloadRecommendedSignatureById(pluginId: String) = ProjectAction(pluginId) { implicit request =>
    sendSignatureFile(request.project.recommendedVersion)
  }

  private def sendSignatureFile(version: Version)(implicit request: Request[_]): Result = {
    val project = version.project
    if (project.visibility == VisibilityTypes.SoftDelete) {
      return notFound
    }
    val path = this.fileManager.getVersionDir(project.ownerName, project.name, version.name).resolve(version.signatureFileName)
    if (notExists(path)) {
      Logger.warn("project version missing signature file")
      notFound
    } else
      Ok.sendFile(path.toFile)
  }

}
