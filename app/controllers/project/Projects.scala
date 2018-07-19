package controllers.project

import java.nio.file.{Files, Path}

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import controllers.sugar.Requests.{AuthRequest, AuthedProjectRequest}
import db.ModelService
import discourse.OreDiscourseApi
import form.OreForms
import javax.inject.Inject
import models.project.{Note, Project, VisibilityTypes}
import models.user._
import ore.permission._
import ore.project.factory.ProjectFactory
import ore.project.io.{InvalidPluginFileException, PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyTypes
import ore.user.MembershipDossier._
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.{AsyncCacheApi, SyncCacheApi}
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import _root_.util.StringUtils._
import com.github.tminglei.slickpg.InetString
import ore.permission.scope.GlobalScope
import views.html.{projects => views}
import db.impl.OrePostgresDriver.api._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

import db.impl.{ProjectMembersTable, ProjectRoleTable}
import models.user.role.ProjectRole
import ore.project.ProjectMember
import ore.user.MembershipDossier
import play.api.mvc.{Action, AnyContent, Result}
import util.functional.{EitherT, Id, OptionT}
import util.instances.future._
import util.syntax._

/**
  * Controller for handling Project related actions.
  */
class Projects @Inject()(stats: StatTracker,
                         forms: OreForms,
                         factory: ProjectFactory,
                         implicit val syncCache: SyncCacheApi,
                         implicit override val cache: AsyncCacheApi,
                         implicit override val bakery: Bakery,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit val forums: OreDiscourseApi,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)(implicit val ec: ExecutionContext)
                         extends OreBaseController {


  implicit val fileManager: ProjectFiles = factory.fileManager

  private val self = controllers.project.routes.Projects

  private def SettingsEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditSettings)

  /**
    * Displays the "create project" page.
    *
    * @return Create project view
    */
  def showCreator(): Action[AnyContent] = UserLock() async { implicit request =>

    for {
      orgas <- request.user.organizations.all
      createOrga <- Future.sequence(orgas.map(orga => request.user can CreateProject in orga))
    } yield {
      val createdOrgas = orgas zip createOrga collect {
        case (orga, true) => orga
      }
      Ok(views.create(createdOrgas.toSeq, None))
    }
  }

  /**
    * Uploads a Project's first plugin file for further processing.
    *
    * @return Result
    */
  def upload(): Action[AnyContent] = UserLock() { implicit request =>
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
                Redirect(self.showCreator()).withErrors(Option(e.getMessage).toList)
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
  def showCreatorWithMeta(author: String, slug: String): Action[AnyContent] = UserLock().async { implicit request =>
    this.factory.getPendingProject(author, slug) match {
      case None =>
        Future.successful(Redirect(self.showCreator()).withError("error.project.timeout"))
      case Some(pending) =>
        for {
          (orgas, owner) <- (request.user.organizations.all, pending.underlying.owner.user).parTupled
          createOrga <- Future.sequence(orgas.map(orga => owner can CreateProject in orga))
        } yield {
          val createdOrgas = orgas zip createOrga filter (_._2) map (_._1)
          Ok(views.create(createdOrgas.toSeq, Some(pending)))
        }
    }
  }

  /**
    * Shows the members invitation page during Project creation.
    *
    * @param author   Project owner
    * @param slug     Project slug
    * @return         View of members config
    */
  def showInvitationForm(author: String, slug: String): Action[AnyContent] = UserLock().async { implicit request =>
    orgasUserCanUploadTo(request.user) flatMap { organisationUserCanUploadTo =>
        this.factory.getPendingProject(author, slug) match {
          case None => Future.successful(Redirect(self.showCreator()).withError("error.project.timeout"))
          case Some(pendingProject) =>
            this.forms.ProjectSave(organisationUserCanUploadTo.toSeq).bindFromRequest().fold(
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
                implicit val currentUser: User = request.user

                val authors = pendingProject.file.meta.get.getAuthors.asScala
                (
                  Future.sequence(authors.filterNot(_.equals(currentUser.username)).map(this.users.withName(_).value)),
                  this.forums.countUsers(authors.toList),
                  pendingProject.underlying.owner.user
                ).parMapN { (users, registered, owner) =>
                  Ok(views.invite(owner, pendingProject, users.flatten.toList, registered))
                }
              }
            )
        }
      }
  }

  private def orgasUserCanUploadTo(user: User): Future[Set[Int]] = {
    for {
      all <- user.organizations.all
      canCreate <- Future.traverse(all)(org => user can CreateProject in org map { perm => (org.id.get, perm)})
    } yield {
      // Filter by can Create Project
      val others = canCreate.collect {
        case (id, perm) if perm => id
      }

      others + user.id.get // Add self
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
  def showFirstVersionCreator(author: String, slug: String): Action[AnyContent] = UserLock() { implicit request =>
    val res = for {
      pendingProject <- EitherT.fromOption[Id](this.factory.getPendingProject(author, slug), Redirect(self.showCreator()).withError(


        "error.project.timeout"))
      roles <- bindFormEitherT[Id](this.forms.ProjectMemberRoles)(_ => BadRequest: Result)
    } yield {
      pendingProject.roles = roles.build()
      val pendingVersion = pendingProject.pendingVersion
      Redirect(routes.Versions.showCreatorWithMeta(author, slug, pendingVersion.underlying.versionString))
    }

    res.merge
  }

  /**
    * Displays the Project with the specified author and name.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return View of project
    */
  def show(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) async { request =>
    val data = request.data
    implicit val r: Requests.OreRequest[AnyContent] = request.request

    projects.queryProjectPages(data.project) flatMap { pages =>
      val pageCount = pages.size + pages.map(_._2.size).sum
      this.stats.projectViewed(request)(request => Ok(views.pages.view(data, request.scoped, pages, data.project.homePage, None, pageCount)))
    }
  }

  /**
    * Shortcut for navigating to a project.
    *
    * @param pluginId Project pluginId
    * @return Redirect to project page.
    */
  def showProjectById(pluginId: String): Action[AnyContent] = OreAction async { implicit request =>
    this.projects.withPluginId(pluginId).fold(notFound) { project =>
      Redirect(self.show(project.ownerName, project.slug))
    }
  }

  /**
    * Displays the "discussion" tab within a Project view.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return View of project
    */
  def showDiscussion(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) async { request =>
    implicit val r: Requests.OreRequest[AnyContent] = request.request
    this.stats.projectViewed(request)(request => Ok(views.discuss(request.data, request.scoped)))
  }

  /**
    * Posts a new discussion reply to the forums.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of discussion with new post
    */
  def postDiscussionReply(author: String, slug: String): Action[AnyContent] = AuthedProjectAction(author, slug) async { implicit request =>
    this.forms.ProjectReply.bindFromRequest.fold(
      hasErrors =>
        Future.successful(Redirect(self.showDiscussion(author, slug)).withFormErrors(hasErrors.errors)),
      formData => {
        val data = request.data
        if (data.project.topicId == -1)
          Future.successful(BadRequest)
        else {
          // Do forum post and display errors to user if any
          for {
            poster <- {
              OptionT.fromOption[Future](formData.poster)
                .flatMap(posterName => this.users.requestPermission(request.user, posterName, PostAsOrganization))
                .getOrElse(request.user)
            }
            errors <- this.forums.postDiscussionReply(data.project, poster, formData.content)
          } yield {
            val result = Redirect(self.showDiscussion(author, slug))
            if (errors.nonEmpty) result.withErrors(errors) else result
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
  def showIssues(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug)  { implicit request =>
    implicit val r: Requests.OreRequest[AnyContent] = request.request
    request.data.settings.issues match {
      case None => notFound
      case Some(link) => Redirect(link)
    }
  }

  /**
    * Redirect's to the project's source code if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Source code
    */
  def showSource(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug)  { implicit request =>
    implicit val r: Requests.OreRequest[AnyContent] = request.request
    request.data.settings.source match {
      case None => notFound
      case Some(link) => Redirect(link)
    }
  }

  /**
    * Shows either a customly uploaded icon for a [[models.project.Project]]
    * or the owner's avatar if there is none.
    *
    * @param author Project owner
    * @param slug Project slug
    * @return Project icon
    */
  def showIcon(author: String, slug: String): Action[AnyContent] = Action async { implicit request =>
    // TODO maybe instead of redirect cache this on ore?
    this.projects.withSlug(author, slug).semiFlatMap { project =>
      this.projects.fileManager.getIconPath(project) match {
        case None =>
          project.owner.user.map(_.avatarUrl.map(Redirect(_)).getOrElse(NotFound))
        case Some(iconPath) =>
          Future.successful(showImage(iconPath))
      }
    }.getOrElse(NotFound)
  }

  private def showImage(path: Path) = Ok(Files.readAllBytes(path)).as("image/jpeg")

  /**
    * Submits a flag on the specified project for further review.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of project
    */
  def flag(author: String, slug: String): Action[AnyContent] = AuthedProjectAction(author, slug).async { implicit request =>
    val user = request.user
    val data = request.data
    user.hasUnresolvedFlagFor(data.project).map {
      // One flag per project, per user at a time
      case true => BadRequest
      case false => this.forms.ProjectFlag.bindFromRequest().fold(
        hasErrors =>
          FormError(ShowProject(data.project), hasErrors),
        formData => {
          data.project.flagFor(user, formData.reason, formData.comment)
          UserActionLogger.log(request.request, LoggedAction.ProjectFlagged, data.project.id.getOrElse(-1), s"Flagged by ${user.name}", s"Not flagged by ${user.name}")
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
  def setWatching(author: String, slug: String, watching: Boolean): Action[AnyContent] = {
    AuthedProjectAction(author, slug) async { implicit request =>
      request.user.setWatching(request.data.project, watching).map(_ => Ok)
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
  def setStarred(author: String, slug: String, starred: Boolean): Action[AnyContent] = {
    AuthedProjectAction(author, slug) { implicit request =>
      if (request.data.project.ownerId != request.user.userId) {
        request.data.project.setStarredBy(request.user, starred)
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
  def setInviteStatus(id: Int, status: String): Action[AnyContent] = Authenticated.async { implicit request =>
    val user = request.user
    user.projectRoles.get(id).semiFlatMap { role =>
      role.project.map { project =>
        val dossier: MembershipDossier {
  type MembersTable = ProjectMembersTable

  type MemberType = ProjectMember

  type RoleTable = ProjectRoleTable

  type ModelType = Project

  type RoleType = ProjectRole
} = project.memberships
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
    }.getOrElse(NotFound)
  }

  /**
    * Shows the project manager or "settings" pane.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Project manager
    */
  def showSettings(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug) async { request =>
    implicit val r: AuthRequest[AnyContent] = request.request
    val projectData = request.data
    projectData.project.apiKeys.find(_.keyType === ProjectApiKeyTypes.Deployment).value.map { deployKey =>
      Ok(views.settings(projectData, request.scoped, deployKey))
    }
  }

  /**
    * Uploads a new icon to be saved for the specified [[models.project.Project]].
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Ok or redirection if no file
    */
  def uploadIcon(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug) { implicit request =>
    request.body.asMultipartFormData.get.file("icon") match {
      case None =>
        Redirect(self.showSettings(author, slug)).withError("error.noFile")
      case Some(tmpFile) =>
        val data = request.data
        val pendingDir = this.projects.fileManager.getPendingIconDir(data.project.ownerName, data.project.name)
        if (Files.notExists(pendingDir))
          Files.createDirectories(pendingDir)
        Files.list(pendingDir).iterator().asScala.foreach(Files.delete)
        tmpFile.ref.moveTo(pendingDir.resolve(tmpFile.filename).toFile, replace = true)
        UserActionLogger.log(request.request, LoggedAction.ProjectIconChanged, data.project.id.getOrElse(-1), "", "") //todo data
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
  def resetIcon(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug) { implicit request =>
    val data = request.data
    val fileManager = this.projects.fileManager
    fileManager.getIconPath(data.project).foreach(Files.delete)
    fileManager.getPendingIconPath(data.project).foreach(Files.delete)
    UserActionLogger.log(request.request, LoggedAction.ProjectIconChanged, data.project.id.getOrElse(-1), "", "") //todo data
    Files.delete(fileManager.getPendingIconDir(data.project.ownerName, data.project.name))
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
  def showPendingIcon(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) { implicit request =>
    val data = request.data
    implicit val r: Requests.OreRequest[AnyContent] = request.request
    this.projects.fileManager.getPendingIconPath(data.project) match {
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
  def removeMember(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).async { implicit request =>
    val res = for {
      name <- bindFormOptionT[Future](this.forms.ProjectMemberRemove)
      user <- this.users.withName(name)
    } yield {
      val project = request.data.project
      project.memberships.removeMember(user)
      UserActionLogger.log(request.request, LoggedAction.ProjectMemberRemoved, project.id.getOrElse(-1),
        s"'$user.name' is not a member of ${project.ownerName}/${project.name}", s"'$user.name' is a member of ${project.ownerName}/${project.name}")
      Redirect(self.showSettings(author, slug))
    }

    res.getOrElse(BadRequest)
  }

  /**
    * Saves the specified Project from the settings manager.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of project
    */
  def save(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).async { implicit request =>
    orgasUserCanUploadTo(request.user) flatMap { organisationUserCanUploadTo =>
      val data = request.data
      this.forms.ProjectSave(organisationUserCanUploadTo.toSeq).bindFromRequest().fold(
        hasErrors =>
          Future.successful(FormError(self.showSettings(author, slug), hasErrors)),
        formData => {
          data.settings.save(data.project, formData).map { _ =>
            UserActionLogger.log(request.request, LoggedAction.ProjectSettingsChanged, request.data.project.id.getOrElse(-1), "", "") //todo add old new data
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
  def rename(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).async { implicit request =>
    val project = request.data.project

    val res = for {
      newName <- bindFormEitherT[Future](this.forms.ProjectRename)(_ => BadRequest).map(compact)
      available <- EitherT.right[Result](projects.isNamespaceAvailable(author, slugify(newName)))
      _ <- EitherT.cond[Future](available, (), Redirect(self.showSettings(author, slug)).withError("error.nameUnavailable"))
      _ <- EitherT.right[Result](this.projects.rename(project, newName))
    } yield {
      val data = request.data
      val oldName = data.project.name
      UserActionLogger.log(request.request, LoggedAction.ProjectRenamed, data.project.id.getOrElse(-1), s"$author/$newName", s"$author/$oldName")
      Redirect(self.show(author, project.slug))
    }

    res.merge
  }

  /**
    * Sets the visible state of the specified Project.
    *
    * @param author     Project owner
    * @param slug       Project slug
    * @param visibility Project visibility
    * @return         Ok
    */
  def setVisible(author: String, slug: String, visibility: Int): Action[AnyContent] = {
    (AuthedProjectAction(author, slug, requireUnlock = true)
      andThen ProjectPermissionAction(HideProjects)) async { implicit request =>
      val newVisibility = VisibilityTypes.withId(visibility)
      request.user can newVisibility.permission in GlobalScope flatMap { perm =>
        if (perm) {
          val oldVisibility = request.data.project.visibility;

          val change = if (newVisibility.showModal) {
            val comment = this.forms.NeedsChanges.bindFromRequest.get.trim
            request.data.project.setVisibility(newVisibility, comment, request.user.id.get)
          } else {
            request.data.project.setVisibility(newVisibility, "", request.user.id.get)
          }
          UserActionLogger.log(request.request, LoggedAction.ProjectVisibilityChange, request.data.project.id.getOrElse(-1), newVisibility.nameKey, VisibilityTypes.NeedsChanges.nameKey)
          change.map(_ => Ok)
        } else {
          Future.successful(Unauthorized)
        }
      }
    }
  }

  /**
    * Set a project that is in new to public
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def publish(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug) { implicit request =>
    val data = request.data
    if (data.visibility == VisibilityTypes.New) {
      data.project.setVisibility(VisibilityTypes.Public, "", request.user.id.get)
      UserActionLogger.log(request.request, LoggedAction.ProjectVisibilityChange, data.project.id.getOrElse(-1), VisibilityTypes.Public.nameKey, VisibilityTypes.New.nameKey)
    }
    Redirect(self.show(data.project.ownerName, data.project.slug))
  }

  /**
    * Set a project that needed changes to the approval state
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def sendForApproval(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug) { implicit request =>
    val data = request.data
    if (data.visibility == VisibilityTypes.NeedsChanges) {
      data.project.setVisibility(VisibilityTypes.NeedsApproval, "", request.user.id.get)
      UserActionLogger.log(request.request, LoggedAction.ProjectVisibilityChange, data.project.id.getOrElse(-1), VisibilityTypes.NeedsApproval.nameKey, VisibilityTypes.NeedsChanges.nameKey)
    }
    Redirect(self.show(data.project.ownerName, data.project.slug))
  }

  def showLog(author: String, slug: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ViewLogs)) andThen ProjectAction(author, slug) async { request =>
      implicit val r: Requests.OreRequest[AnyContent] = request.request
      val project = request.data.project
      for {
        (changes, logger) <- (project.visibilityChangesByDate, project.logger).parTupled
        (changedBy, logs) <- (Future.sequence(changes.map(_.created.value)), logger.entries.all).parTupled
      } yield {
        val visChanges = changes zip changedBy
        Ok(views.log(project, visChanges, logs.toSeq))
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
  def delete(author: String, slug: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](HardRemoveProject)).async { implicit request =>
      getProject(author, slug).map { project =>
        this.projects.delete(project)
        UserActionLogger.log(request, LoggedAction.ProjectVisibilityChange, project.id.getOrElse(-1), "null", project.visibility.nameKey)
        Redirect(ShowHome).withSuccess(this.messagesApi("project.deleted", project.name))
      }.merge
    }
  }

  /**
    * Soft deletes the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Home page
    */
  def softDelete(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).async { implicit request =>
    val data = request.data
    val comment = this.forms.NeedsChanges.bindFromRequest.get.trim
    data.project.setVisibility(VisibilityTypes.SoftDelete, comment, request.user.id.get).map { _ =>
      UserActionLogger.log(request.request, LoggedAction.ProjectVisibilityChange, data.project.id.getOrElse(-1), VisibilityTypes.SoftDelete.nameKey, data.project.visibility.nameKey)
      Redirect(ShowHome).withSuccess(this.messagesApi("project.deleted", data.project.name))
    }
  }

  /**
    * Show the flags that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showFlags(author: String, slug: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)) andThen ProjectAction(author, slug) async { request =>
      implicit val r: Requests.OreRequest[AnyContent] = request.request
      getProject(author, slug).map { project =>
        Ok(views.admin.flags(request.data))
      }.merge
    }
  }

  /**
    * Show the notes that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showNotes(author: String, slug: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)).async { implicit request =>
      getProject(author, slug).semiFlatMap { project =>
        Future.sequence(project.getNotes().map(note => users.get(note.user).value.map(user => (note, user)))) map { notes =>
          Ok(views.admin.notes(project, notes))
        }
      }.merge
    }
  }

  def addMessage(author: String, slug: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      val res = for {
        project <- getProject(author, slug)
        description <- bindFormEitherT[Future](this.forms.NoteDescription)(_ => BadRequest: Result)
      } yield {
        project.addNote(Note(description.trim, request.user.userId))
        Ok("Review")
      }

      res.merge
    }
  }
}
