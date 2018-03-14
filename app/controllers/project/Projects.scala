package controllers.project

import java.nio.file.{Files, Path}
import javax.inject.Inject

import _root_.util.StringUtils._
import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.ModelService
import discourse.OreDiscourseApi
import form.OreForms
import models.project.{Note, VisibilityTypes}
import models.user.User
import models.viewhelper.{HeaderData, ProjectData}
import ore.permission._
import ore.permission.scope.GlobalScope
import ore.project.factory.ProjectFactory
import ore.project.io.{InvalidPluginFileException, PluginUpload}
import ore.user.MembershipDossier._
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.SyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import views.html.{projects => views}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for handling Project related actions.
  */
class Projects @Inject()(stats: StatTracker,
                         forms: OreForms,
                         factory: ProjectFactory,
                         implicit val cache: SyncCacheApi,
                         implicit override val bakery: Bakery,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit val forums: OreDiscourseApi,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)
                         extends OreBaseController {



  implicit val fileManager = factory.fileManager

  private val self = controllers.project.routes.Projects

  private def SettingsEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditSettings)

  /**
    * Displays the "create project" page.
    *
    * @return Create project view
    */
  def showCreator() = UserLock() { implicit request =>
    val headerData: HeaderData = null // TODO headerData
    Ok(views.create(headerData, None))
  }

  /**
    * Uploads a Project's first plugin file for further processing.
    *
    * @return Result
    */
  def upload() = UserLock() { implicit request =>
    val user = request.user
    this.factory.getUploadError(user) match {
      case Some(error) =>
        Redirect(self.showCreator()).withError(error)
      case None =>
        PluginUpload.bindFromRequest() match {
          case None =>
            Redirect(self.showCreator()).withError("error.noFile")
          case Some(uploadData) =>
            try {
              val plugin = this.factory.processPluginUpload(uploadData, user)
              val project = this.factory.startProject(plugin)
              project.cache()
              val model = project.underlying
              Redirect(self.showCreatorWithMeta(model.ownerName, model.slug))
            } catch {
              case e: InvalidPluginFileException =>
                Redirect(self.showCreator()).withError(Option(e.getMessage).getOrElse(""))
            }
        }
    }
  }

  /**
    * Displays the "create project" page with uploaded plugin meta data.
    *
    * @param author Author of plugin
    * @param slug   Project slug
    * @return Create project view
    */
  def showCreatorWithMeta(author: String, slug: String) = UserLock() { implicit request =>
    this.factory.getPendingProject(author, slug) match {
      case None =>
        Redirect(self.showCreator())
      case Some(pending) =>
        val headerData: HeaderData = null // TODO headerdata
        Ok(views.create(headerData, Some(pending)))
    }
  }

  /**
    * Shows the members invitation page during Project creation.
    *
    * @param author   Project owner
    * @param slug     Project slug
    * @return         View of members config
    */
  def showInvitationForm(author: String, slug: String) = UserLock().async { implicit request =>
    request.user.organizations.all.flatMap { all =>
      val organisationUserCanUploadTo = all.filter(request.user can CreateProject in _).map(_.id.get).toSeq :+ request.user.id.get
      this.factory.getPendingProject(author, slug) match {
        case None => Future.successful(Redirect(self.showCreator()))
        case Some(pendingProject) =>
          this.forms.ProjectSave(organisationUserCanUploadTo).bindFromRequest().fold(
            hasErrors =>
              Future.successful(FormError(self.showCreator(), hasErrors)),
            formData => {
              pendingProject.settings.save(pendingProject.underlying, formData)
              // update cache for name changes
              val project = pendingProject.underlying
              val version = pendingProject.pendingVersion
              val namespace = project.namespace
              this.cache.set(namespace, pendingProject)
              this.cache.set(namespace + '/' + version.underlying.versionString, version)

              implicit val currentUser = request.user

              val authors = pendingProject.file.meta.get.getAuthors.asScala
              for {
                users <- Future.sequence(authors.filterNot(_.equals(currentUser.username)).map(this.users.withName))
                registered <- this.forums.countUsers(authors.toList)
              } yield {
                val headerData: HeaderData = null // TODO get my data
                Ok(views.invite(pendingProject, users.flatten.toList, registered, headerData))
              }
            }
          )
      }
    }
  }

  /**
    * Continues on to the second step of Project creation where the user
    * publishes their Project.
    *
    * @param author Author of project
    * @param slug   Project slug
    * @return Redirection to project page if successful
    */
  def showFirstVersionCreator(author: String, slug: String) = UserLock() { implicit request =>
    this.factory.getPendingProject(author, slug) match {
      case None =>
        Redirect(self.showCreator())
      case Some(pendingProject) =>
        pendingProject.roles = this.forms.ProjectMemberRoles.bindFromRequest.get.build()
        val pendingVersion = pendingProject.pendingVersion
        Redirect(routes.Versions.showCreatorWithMeta(author, slug, pendingVersion.underlying.versionString))
    }
  }

  /**
    * Displays the Project with the specified author and name.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return View of project
    */
  def show(author: String, slug: String) = ProjectAction(author, slug) { implicit request =>
    val project = request.project
    this.stats.projectViewed(implicit request => Ok(views.pages.view(project, project.homePage)))
  }

  /**
    * Shortcut for navigating to a project.
    *
    * @param pluginId Project pluginId
    * @return Redirect to project page.
    */
  def showProjectById(pluginId: String) = Action.async { implicit request =>
    this.projects.withPluginId(pluginId).map {
      case None => notFound
      case Some(project) => Redirect(self.show(project.ownerName, project.slug))
    }
  }

  /**
    * Displays the "discussion" tab within a Project view.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return View of project
    */
  def showDiscussion(author: String, slug: String) = ProjectAction(author, slug) { implicit request =>
    this.stats.projectViewed(implicit request => Ok(views.discuss(request.project)))
  }

  /**
    * Posts a new discussion reply to the forums.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of discussion with new post
    */
  def postDiscussionReply(author: String, slug: String) = AuthedProjectAction(author, slug) async { implicit request =>
    this.forms.ProjectReply.bindFromRequest.fold(
      hasErrors =>
        Future(Redirect(self.showDiscussion(author, slug)).withError(hasErrors.errors.head.message)),
      formData => {
        val project = request.project
        if (project.topicId == -1)
          Future(BadRequest)
        else {
          // Do forum post and display errors to user if any
          val poster = formData.poster match {
            case None => Future(request.user)
            case Some(posterName) =>
              this.users.requestPermission(request.user, posterName, PostAsOrganization).map {
                case None => request.user // No Permission ; Post as self instead
                case Some(user) => user   // Permission granted
              }
          }
          val errors = poster.flatMap(post => this.forums.postDiscussionReply(project, post, formData.content))
          errors.map { errList =>
            val result = Redirect(self.showDiscussion(author, slug))
            if (errList.nonEmpty) result.withError(errList.head) else result
          }
        }
      }
    )
  }

  /**
    * Redirect's to the project's issue tracker if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Issue tracker
    */
  def showIssues(author: String, slug: String) = ProjectAction(author, slug).async { implicit request =>
    request.project.settings.map(_.issues match {
      case None => notFound
      case Some(link) => Redirect(link)
    })
  }

  /**
    * Redirect's to the project's source code if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Source code
    */
  def showSource(author: String, slug: String) = ProjectAction(author, slug).async { implicit request =>
    request.project.settings.map(_.source match {
      case None => notFound
      case Some(link) => Redirect(link)
    })
  }

  /**
    * Shows either a customly uploaded icon for a [[models.project.Project]]
    * or the owner's avatar if there is none.
    *
    * @param author Project owner
    * @param slug Project slug
    * @return Project icon
    */
  def showIcon(author: String, slug: String) = ProjectAction(author, slug).async { implicit request =>
    val project = request.project
    this.projects.fileManager.getIconPath(project) match {
      case None =>
        project.owner.user.map(_.avatarUrl.map(Redirect(_)).getOrElse(notFound))
      case Some(iconPath) =>
        Future.successful(showImage(iconPath))
    }
  }

  private def showImage(path: Path) = Ok(Files.readAllBytes(path)).as("image/jpeg")

  /**
    * Submits a flag on the specified project for further review.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of project
    */
  def flag(author: String, slug: String) = AuthedProjectAction(author, slug).async { implicit request =>
    val user = request.user
    val project = request.project
    user.hasUnresolvedFlagFor(project).map {
      // One flag per project, per user at a time
      case true => BadRequest
      case false => this.forms.ProjectFlag.bindFromRequest().fold(
        hasErrors =>
          FormError(ShowProject(project), hasErrors),
        formData => {
          project.flagFor(user, formData.reason, formData.comment)
          Redirect(self.show(author, slug)).flashing("reported" -> "true")
        }
      )
    }
  }

  /**
    * Sets whether a [[models.user.User]] is watching a project.
    *
    * @param author   Project owner
    * @param slug     Project slug
    * @param watching True if watching
    * @return         Ok
    */
  def setWatching(author: String, slug: String, watching: Boolean) = {
    AuthedProjectAction(author, slug) { implicit request =>
      request.user.setWatching(request.project, watching)
      Ok
    }
  }

  /**
    * Sets the "starred" status of a Project for the current user.
    *
    * @param author  Project owner
    * @param slug    Project slug
    * @param starred True if should set to starred
    * @return Result code
    */
  def setStarred(author: String, slug: String, starred: Boolean) = {
    AuthedProjectAction(author, slug) { implicit request =>
      if (request.project.ownerId != request.user.userId) {
        request.project.setStarredBy(request.user, starred)
        Ok
      } else {
        BadRequest
      }
    }
  }

  /**
    * Sets the status of a pending Project invite for the current user.
    *
    * @param id     Invite ID
    * @param status Invite status
    * @return       NotFound if invite doesn't exist, Ok otherwise
    */
  def setInviteStatus(id: Int, status: String) = Authenticated.async { implicit request =>
    val user = request.user
    user.projectRoles.get(id).flatMap {
      case None => Future.successful(notFound)
      case Some(role) =>
        role.project.map(_.memberships).map { dossier =>
          status match {
            case STATUS_DECLINE =>
              dossier.removeRole(role)
              Ok
            case STATUS_ACCEPT =>
              role.setAccepted(true)
              Ok
            case STATUS_UNACCEPT =>
              role.setAccepted(false)
              Ok
            case _ =>
              BadRequest
          }
        }
    }
  }

  /**
    * Shows the project manager or "settings" pane.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Project manager
    */
  def showSettings(author: String, slug: String) = SettingsEditAction(author, slug) { implicit request =>
    val projectData: ProjectData = null // TODO projectdata
    val deployKey = None // TODO deployKey
    Ok(views.settings(projectData, deployKey))
  }

  /**
    * Uploads a new icon to be saved for the specified [[models.project.Project]].
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Ok or redirection if no file
    */
  def uploadIcon(author: String, slug: String) = SettingsEditAction(author, slug) { implicit request =>
    request.body.asMultipartFormData.get.file("icon") match {
      case None =>
        Redirect(self.showSettings(author, slug)).withError("error.noFile")
      case Some(tmpFile) =>
        val project = request.project
        val pendingDir = this.projects.fileManager.getPendingIconDir(project.ownerName, project.name)
        if (Files.notExists(pendingDir))
          Files.createDirectories(pendingDir)
        Files.list(pendingDir).iterator().asScala.foreach(Files.delete)
        tmpFile.ref.moveTo(pendingDir.resolve(tmpFile.filename).toFile, replace = true)
        Ok
    }
  }

  /**
    * Resets the specified Project's icon to the default user avatar.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Ok
    */
  def resetIcon(author: String, slug: String) = SettingsEditAction(author, slug) { implicit request =>
    val project = request.project
    val fileManager = this.projects.fileManager
    fileManager.getIconPath(project).foreach(Files.delete)
    fileManager.getPendingIconPath(project).foreach(Files.delete)
    Files.delete(fileManager.getPendingIconDir(project.ownerName, project.name))
    Ok
  }

  /**
    * Displays the specified [[models.project.Project]]'s current pending
    * icon, if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Pending icon
    */
  def showPendingIcon(author: String, slug: String) = ProjectAction(author, slug) { implicit request =>
    val project = request.project
    this.projects.fileManager.getPendingIconPath(project) match {
      case None => notFound
      case Some(path) => showImage(path)
    }
  }

  /**
    * Removes a [[ore.project.ProjectMember]] from the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def removeMember(author: String, slug: String) = SettingsEditAction(author, slug).async { implicit request =>
    this.users.withName(this.forms.ProjectMemberRemove.bindFromRequest.get.trim).map {
      case None => BadRequest
      case Some(user) =>
        request.project.memberships.removeMember(user)
        Redirect(self.showSettings(author, slug))
    }
  }

  /**
    * Saves the specified Project from the settings manager.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of project
    */
  def save(author: String, slug: String) = SettingsEditAction(author, slug).async { implicit request =>
    request.user.organizations.all.flatMap { all =>
      val organisationUserCanUploadTo = all.filter(request.user can CreateProject in _).map(_.id.get).toSeq :+ request.user.id.get
      val project = request.project
      this.forms.ProjectSave(organisationUserCanUploadTo).bindFromRequest().fold(
        hasErrors =>
          Future.successful(FormError(self.showSettings(author, slug), hasErrors)),
        formData => {
          project.settings.map { settings =>
            settings.save(project, formData)
            Redirect(self.show(author, slug))
          }
        }
      )
    }
  }

  /**
    * Renames the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Project homepage
    */
  def rename(author: String, slug: String) = SettingsEditAction(author, slug).async { implicit request =>
    val newName = compact(this.forms.ProjectRename.bindFromRequest.get)
    projects.isNamespaceAvailable(author, slugify(newName)).flatMap {
      case false => Future(Redirect(self.showSettings(author, slug)).withError("error.nameUnavailable"))
      case true =>
        val project = request.project
        this.projects.rename(project, newName).map { _ =>
          Redirect(self.show(author, project.slug))
        }
    }
  }

  /**
    * Sets the visible state of the specified Project.
    *
    * @param author     Project owner
    * @param slug       Project slug
    * @param visibility Project visibility
    * @return         Ok
    */
  def setVisible(author: String, slug: String, visibility: Int) = {
    (AuthedProjectAction(author, slug, requireUnlock = true)
      andThen ProjectPermissionAction(HideProjects)) { implicit request =>
      val newVisibility = VisibilityTypes.withId(visibility)
      if (request.user can newVisibility.permission in GlobalScope) {
        if (newVisibility.showModal) {
          val comment = this.forms.NeedsChanges.bindFromRequest.get.trim
          request.project.setVisibility(newVisibility, comment, request.user)
        } else {
          request.project.setVisibility(newVisibility, "", request.user)
        }
      }
      Ok
    }
  }

  /**
    * Set a project that is in new to public
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def publish(author: String, slug: String) = SettingsEditAction(author, slug) { implicit request =>
    val project = request.project
    if (project.visibility == VisibilityTypes.New) {
      project.setVisibility(VisibilityTypes.Public, "", request.user)
    }
    Redirect(self.show(project.ownerName, project.slug))
  }

  /**
    * Set a project that needed changes to the approval state
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def sendForApproval(author: String, slug: String) = SettingsEditAction(author, slug) { implicit request =>
    val project = request.project
    if (project.visibility == VisibilityTypes.NeedsChanges) {
      project.setVisibility(VisibilityTypes.NeedsApproval, "", request.user)
    }
    Redirect(self.show(project.ownerName, project.slug))
  }

  def showLog(author: String, slug: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ViewLogs)).async { implicit request =>
      withProject(author, slug) { project =>
        Ok(views.log(project))
      }
    }
  }

  /**
    * Irreversibly deletes the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def delete(author: String, slug: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](HardRemoveProject)).async { implicit request =>
      withProject(author, slug) { project =>
        this.projects.delete(project)
        Redirect(ShowHome).withSuccess(this.messagesApi("project.deleted", project.name))
      }
    }
  }

  /**
    * Soft deletes the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def softDelete(author: String, slug: String) = SettingsEditAction(author, slug).async { implicit request =>
    val project = request.project
    val comment = this.forms.NeedsChanges.bindFromRequest.get.trim
    project.setVisibility(VisibilityTypes.SoftDelete, comment, request.user).map(vc =>
      Redirect(ShowHome).withSuccess(this.messagesApi("project.deleted", project.name)))
  }

  /**
    * Show the flags that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showFlags(author: String, slug: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)).async { implicit request =>
      withProject(author, slug) { project =>
        Ok(views.admin.flags(project))
      }
    }
  }

  /**
    * Show the notes that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showNotes(author: String, slug: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)).async { implicit request =>
      withProject(author, slug) { project =>
        Ok(views.admin.notes(project))
      }
    }
  }

  def addMessage(author: String, slug: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      withProject(author, slug) { project =>
        project.addNote(Note(this.forms.NoteDescription.bindFromRequest.get.trim, request.user.userId))
        Ok("Review")
      }
    }
  }
}