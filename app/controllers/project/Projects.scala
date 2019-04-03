package controllers.project

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}

import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.{Model, DbRef, ModelService}
import discourse.OreDiscourseApi
import form.OreForms
import form.project.{DiscussionReplyForm, FlagForm, ProjectRoleSetBuilder}
import models.admin.ProjectLogEntry
import models.api.ProjectApiKey
import models.project.{Flag, Note, Page, Visibility}
import models.user._
import models.user.role.ProjectUserRole
import models.viewhelper.ScopedOrganizationData
import ore.permission._
import ore.project.factory.ProjectFactory
import ore.project.io.{PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyType
import ore.user.MembershipDossier
import ore.user.MembershipDossier._
import ore.{OreConfig, OreEnv, StatTracker}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import _root_.util.StringUtils._
import _root_.util.syntax._
import util.OreMDC
import views.html.{projects => views}

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.syntax.all._
import cats.instances.option._
import com.typesafe.scalalogging

/**
  * Controller for handling Project related actions.
  */
class Projects @Inject()(stats: StatTracker, forms: OreForms, factory: ProjectFactory)(
    implicit val ec: ExecutionContext,
    cache: AsyncCacheApi,
    bakery: Bakery,
    sso: SingleSignOnConsumer,
    auth: SpongeAuthApi,
    forums: OreDiscourseApi,
    messagesApi: MessagesApi,
    env: OreEnv,
    config: OreConfig,
    service: ModelService
) extends OreBaseController {

  implicit val fileManager: ProjectFiles = factory.fileManager

  private val self = controllers.project.routes.Projects

  private val Logger    = scalalogging.Logger("Projects")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  private def SettingsEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.EditProjectSettings))

  private def MemberEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.ManageProjectMembers))

  /**
    * Displays the "create project" page.
    *
    * @return Create project view
    */
  def showCreator(): Action[AnyContent] = UserLock().asyncF { implicit request =>
    import cats.instances.vector._
    for {
      orgas      <- request.user.organizations.allFromParent
      createOrga <- orgas.toVector.parTraverse(request.user.permissionsIn(_).map(_.has(Permission.CreateProject)))
    } yield {
      val createdOrgas = orgas.zip(createOrga).collect {
        case (orga, true) => orga
      }
      Ok(views.create(createdOrgas, None))
    }
  }

  /**
    * Uploads a Project's first plugin file for further processing.
    *
    * @return Result
    */
  def upload(): Action[AnyContent] = UserLock().asyncEitherT { implicit request =>
    val user = request.user

    val res = for {
      _          <- EitherT.fromOption[IO](factory.getUploadError(user), ()).swap
      uploadData <- EitherT.fromOption[IO](PluginUpload.bindFromRequest(), "error.noFile")
      pluginFile <- factory.processPluginUpload(uploadData, user)
      project = factory.startProject(pluginFile)
      _ <- EitherT.right[String](project.cache)
    } yield Redirect(self.showCreatorWithMeta(project.ownerName, project.slug))

    res.leftMap(Redirect(self.showCreator()).withError(_))
  }

  /**
    * Displays the "create project" page with uploaded plugin meta data.
    *
    * @param author Author of plugin
    * @param slug   Project slug
    * @return Create project view
    */
  def showCreatorWithMeta(author: String, slug: String): Action[AnyContent] = UserLock().asyncF { implicit request =>
    this.factory.getPendingProject(author, slug) match {
      case None => IO.pure(Redirect(self.showCreator()).withError("error.project.timeout"))
      case Some(pending) =>
        import cats.instances.vector._
        for {
          t <- (request.user.organizations.allFromParent, pending.owner).parTupled
          (orgas, owner) = t
          createOrga <- orgas.toVector.parTraverse(request.user.permissionsIn(_).map(_.has(Permission.CreateProject)))
        } yield {
          val createdOrgas = orgas.zip(createOrga).collect {
            case (orga, true) => orga
          }
          Ok(views.create(createdOrgas, Some(pending)))
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
  def showInvitationForm(author: String, slug: String): Action[AnyContent] = UserLock().asyncF { implicit request =>
    orgasUserCanUploadTo(request.user).flatMap { organisationUserCanUploadTo =>
      this.factory.getPendingProject(author, slug) match {
        case None =>
          IO.pure(Redirect(self.showCreator()).withError("error.project.timeout"))
        case Some(pendingProject) =>
          import cats.instances.list._
          this.forms
            .ProjectSave(organisationUserCanUploadTo.toSeq)
            .bindFromRequest()
            .fold(
              FormErrorLocalized(self.showCreator()).andThen(IO.pure),
              formData => {
                pendingProject.settings.save(pendingProject, formData).flatMap {
                  case (newProject, newSettings) =>
                    val newPending = newProject.copy(settings = newSettings)

                    val authors = newPending.file.data.authors.toList
                    (
                      pendingProject.free *> newPending.cache *>
                        pendingProject.pendingVersion.free *> newPending.pendingVersion.cache,
                      authors.filter(_ != request.user.name).parTraverse(users.withName(_).value),
                      this.forums.countUsers(authors),
                      newPending.owner
                    ).parMapN { (_, users, registered, owner) =>
                      Ok(views.invite(owner, newPending, users.flatten, registered))
                    }
                }
              }
            )
      }
    }
  }

  private def orgasUserCanUploadTo(user: Model[User]): IO[Set[DbRef[Organization]]] = {
    import cats.instances.vector._
    for {
      all <- user.organizations.allFromParent
      canCreate <- all.toVector.parTraverse(
        org => user.permissionsIn(org).map(_.has(Permission.CreateProject)).tupleLeft(org.id.value)
      )
    } yield {
      // Filter by can Create Project
      val others = canCreate.collect {
        case (id, true) => id
      }

      others.toSet + user.id // Add self
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
  def showFirstVersionCreator(author: String, slug: String): Action[ProjectRoleSetBuilder] =
    UserLock()(parse.form(forms.ProjectMemberRoles)).asyncF { implicit request =>
      this.factory
        .getPendingProject(author, slug)
        .toRight(IO.pure(Redirect(self.showCreator()).withError("error.project.timeout")))
        .map { pendingProject =>
          val newPending     = pendingProject.copy(roles = request.body.build())
          val pendingVersion = newPending.pendingVersion
          newPending.cache.as(
            Redirect(routes.Versions.showCreatorWithMeta(author, slug, pendingVersion.versionString))
          )
        }
        .merge
    }

  /**
    * Displays the Project with the specified author and name.
    *
    * @param author Owner of project
    * @param slug   Project slug
    * @return View of project
    */
  def show(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug).asyncF { implicit request =>
    for {
      t <- (projects.queryProjectPages(request.project), request.project.homePage).parTupled
      (pages, homePage) = t
      pageCount         = pages.size + pages.map(_._2.size).sum
      res <- stats.projectViewed(
        Ok(
          views.pages.view(
            request.data,
            request.scoped,
            Model.unwrapNested[Seq[(Model[Page], Seq[Page])]](pages),
            homePage,
            None,
            pageCount
          )
        )
      )
    } yield res
  }

  /**
    * Shortcut for navigating to a project.
    *
    * @param pluginId Project pluginId
    * @return Redirect to project page.
    */
  def showProjectById(pluginId: String): Action[AnyContent] = OreAction.asyncF { implicit request =>
    projects.withPluginId(pluginId).fold(notFound) { project =>
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
  def showDiscussion(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug).asyncF {
    implicit request =>
      forums.isAvailableF.flatMap { isAvailable =>
        this.stats.projectViewed(Ok(views.discuss(request.data, request.scoped, isAvailable)))
      }
  }

  /**
    * Posts a new discussion reply to the forums.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of discussion with new post
    */
  def postDiscussionReply(author: String, slug: String): Action[DiscussionReplyForm] =
    AuthedProjectAction(author, slug).asyncF(
      parse.form(forms.ProjectReply, onErrors = FormError(self.showDiscussion(author, slug)))
    ) { implicit request =>
      val formData = request.body
      if (request.project.topicId.isEmpty)
        IO.pure(BadRequest)
      else {
        // Do forum post and display errors to user if any
        for {
          poster <- {
            OptionT
              .fromOption[IO](formData.poster)
              .flatMap(posterName => users.requestPermission(request.user, posterName, Permission.PostAsOrganization))
              .getOrElse(request.user)
          }
          errors <- this.forums.postDiscussionReply(request.project, poster, formData.content).swap.getOrElse(Nil)
        } yield Redirect(self.showDiscussion(author, slug)).withErrors(errors)
      }
    }

  /**
    * Redirect's to the project's issue tracker if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Issue tracker
    */
  def showIssues(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) { implicit request =>
    request.data.settings.issues match {
      case None       => notFound
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
  def showSource(author: String, slug: String): Action[AnyContent] = ProjectAction(author, slug) { implicit request =>
    request.data.settings.source match {
      case None       => notFound
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
  def showIcon(author: String, slug: String): Action[AnyContent] = Action.asyncF {
    // TODO maybe instead of redirect cache this on ore?
    projects
      .withSlug(author, slug)
      .map { project =>
        projects.fileManager.getIconPath(project)(OreMDC.NoMDC) match {
          case None           => Redirect(User.avatarUrl(project.ownerName))
          case Some(iconPath) => showImage(iconPath)
        }
      }
      .getOrElse(NotFound)
  }

  private def showImage(path: Path) = {
    val lastModified     = Files.getLastModifiedTime(path).toString.getBytes("UTF-8")
    val lastModifiedHash = MessageDigest.getInstance("MD5").digest(lastModified)
    val hashString       = Base64.getEncoder.encodeToString(lastModifiedHash)
    Ok.sendPath(path)
      .withHeaders(ETAG -> s""""$hashString"""", CACHE_CONTROL -> s"max-age=${1.hour.toSeconds.toString}")
  }

  /**
    * Submits a flag on the specified project for further review.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       View of project
    */
  def flag(author: String, slug: String): Action[FlagForm] =
    AuthedProjectAction(author, slug).asyncF(
      parse.form(forms.ProjectFlag, onErrors = FormErrorLocalized(ShowProject(author, slug)))
    ) { implicit request =>
      val user     = request.user
      val project  = request.project
      val formData = request.body

      user.hasUnresolvedFlagFor(project, ModelView.now(Flag)).flatMap {
        // One flag per project, per user at a time
        case true => IO.pure(BadRequest)
        case false =>
          project
            .flagFor(user, formData.reason, formData.comment)
            .productR(
              UserActionLogger.log(
                request.request,
                LoggedAction.ProjectFlagged,
                project.id,
                s"Flagged by ${user.name}",
                s"Not flagged by ${user.name}"
              )
            )
            .as(Redirect(self.show(author, slug)).flashing("reported" -> "true"))
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
  def setWatching(author: String, slug: String, watching: Boolean): Action[AnyContent] =
    AuthedProjectAction(author, slug).asyncF { implicit request =>
      request.user.setWatching(request.project, watching).as(Ok)
    }

  def showStargazers(author: String, slug: String, page: Option[Int]): Action[AnyContent] =
    ProjectAction(author, slug).asyncF { implicit request =>
      val pageSize = this.config.ore.projects.stargazersPageSize
      val pageNum  = math.max(page.getOrElse(1), 1)
      val offset   = (pageNum - 1) * pageSize

      val query = request.project.stars.allQueryFromChild.drop(offset).take(pageSize).sortBy(_.name).result
      service.runDBIO(query).map { users =>
        Ok(views.stargazers(request.data, request.scoped, Model.unwrapNested(users), pageNum, pageSize))
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
  def toggleStarred(author: String, slug: String): Action[AnyContent] =
    AuthedProjectAction(author, slug).asyncF { implicit request =>
      if (request.project.ownerId != request.user.id.value)
        request.data.project.toggleStarredBy(request.user).as(Ok)
      else
        IO.pure(BadRequest)
    }

  /**
    * Sets the status of a pending Project invite for the current user.
    *
    * @param id     Invite ID
    * @param status Invite status
    * @return       NotFound if invite doesn't exist, Ok otherwise
    */
  def setInviteStatus(id: DbRef[ProjectUserRole], status: String): Action[AnyContent] = Authenticated.asyncF {
    implicit request =>
      val user = request.user
      user
        .projectRoles(ModelView.now(ProjectUserRole))
        .get(id)
        .semiflatMap { role =>
          status match {
            case STATUS_DECLINE  => role.project.flatMap(MembershipDossier.project.removeRole(_, role)).as(Ok)
            case STATUS_ACCEPT   => service.update(role)(_.copy(isAccepted = true)).as(Ok)
            case STATUS_UNACCEPT => service.update(role)(_.copy(isAccepted = false)).as(Ok)
            case _               => IO.pure(BadRequest)
          }
        }
        .getOrElse(NotFound)
  }

  /**
    * Sets the status of a pending Project invite on behalf of the Organization
    *
    * @param id     Invite ID
    * @param status Invite status
    * @param behalf Behalf User
    * @return       NotFound if invite doesn't exist, Ok otherwise
    */
  def setInviteStatusOnBehalf(id: DbRef[ProjectUserRole], status: String, behalf: String): Action[AnyContent] =
    Authenticated.asyncF { implicit request =>
      val user = request.user
      val res = for {
        orga       <- organizations.withName(behalf)
        orgaUser   <- users.withName(behalf)
        role       <- orgaUser.projectRoles(ModelView.now(ProjectUserRole)).get(id)
        scopedData <- OptionT.liftF(ScopedOrganizationData.of(Some(user), orga))
        if scopedData.permissions.has(Permission.ManageProjectMembers)
        project <- OptionT.liftF(role.project)
        res <- OptionT.liftF[IO, Status] {
          status match {
            case STATUS_DECLINE  => project.memberships.removeRole(project, role).as(Ok)
            case STATUS_ACCEPT   => service.update(role)(_.copy(isAccepted = true)).as(Ok)
            case STATUS_UNACCEPT => service.update(role)(_.copy(isAccepted = false)).as(Ok)
            case _               => IO.pure(BadRequest)
          }
        }
      } yield res

      res.getOrElse(NotFound)
    }

  /**
    * Shows the project manager or "settings" pane.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Project manager
    */
  def showSettings(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).asyncF {
    implicit request =>
      request.project
        .apiKeys(ModelView.now(ProjectApiKey))
        .find(_.keyType === (ProjectApiKeyType.Deployment: ProjectApiKeyType))
        .value
        .map(deployKey => Ok(views.settings(request.data, request.scoped, deployKey)))
  }

  /**
    * Uploads a new icon to be saved for the specified [[models.project.Project]].
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Ok or redirection if no file
    */
  def uploadIcon(author: String, slug: String): Action[MultipartFormData[TemporaryFile]] =
    SettingsEditAction(author, slug)(parse.multipartFormData).asyncF { implicit request =>
      request.body.file("icon") match {
        case None => IO.pure(Redirect(self.showSettings(author, slug)).withError("error.noFile"))
        case Some(tmpFile) =>
          val data       = request.data
          val pendingDir = projects.fileManager.getPendingIconDir(data.project.ownerName, data.project.name)
          if (Files.notExists(pendingDir))
            Files.createDirectories(pendingDir)
          Files.list(pendingDir).iterator().asScala.foreach(Files.delete)
          tmpFile.ref.moveFileTo(pendingDir.resolve(tmpFile.filename), replace = true)
          //todo data
          UserActionLogger.log(request.request, LoggedAction.ProjectIconChanged, data.project.id, "", "").as(Ok)
      }
    }

  /**
    * Resets the specified Project's icon to the default user avatar.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Ok
    */
  def resetIcon(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).asyncF {
    implicit request =>
      val project     = request.project
      val fileManager = projects.fileManager
      fileManager.getIconPath(project).foreach(Files.delete)
      fileManager.getPendingIconPath(project).foreach(Files.delete)
      //todo data
      Files.delete(fileManager.getPendingIconDir(project.ownerName, project.name))
      UserActionLogger.log(request.request, LoggedAction.ProjectIconChanged, project.id, "", "").as(Ok)
  }

  /**
    * Displays the specified [[models.project.Project]]'s current pending
    * icon, if any.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Pending icon
    */
  def showPendingIcon(author: String, slug: String): Action[AnyContent] =
    ProjectAction(author, slug) { implicit request =>
      projects.fileManager.getPendingIconPath(request.project) match {
        case None       => notFound
        case Some(path) => showImage(path)
      }
    }

  /**
    * Removes a [[ore.project.ProjectMember]] from the specified project.
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def removeMember(author: String, slug: String): Action[String] =
    MemberEditAction(author, slug).asyncF(parse.form(forms.ProjectMemberRemove)) { implicit request =>
      users
        .withName(request.body)
        .semiflatMap { user =>
          val project = request.data.project
          project.memberships
            .removeMember(project, user)
            .productR(
              UserActionLogger.log(
                request.request,
                LoggedAction.ProjectMemberRemoved,
                project.id,
                s"'${user.name}' is not a member of ${project.ownerName}/${project.name}",
                s"'${user.name}' is a member of ${project.ownerName}/${project.name}"
              )
            )
            .as(Redirect(self.showSettings(author, slug)))
        }
        .getOrElse(BadRequest)
    }

  /**
    * Saves the specified Project from the settings manager.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of project
    */
  def save(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).asyncF {
    implicit request =>
      orgasUserCanUploadTo(request.user).flatMap { organisationUserCanUploadTo =>
        val data = request.data
        this.forms
          .ProjectSave(organisationUserCanUploadTo.toSeq)
          .bindFromRequest()
          .fold(
            FormErrorLocalized(self.showSettings(author, slug)).andThen(IO.pure),
            formData => {
              data.settings
                .save(data.project, formData)
                .productR {
                  UserActionLogger.log(
                    request.request,
                    LoggedAction.ProjectSettingsChanged,
                    request.data.project.id,
                    "",
                    ""
                  ) //todo add old new data
                }
                .as(Redirect(self.show(author, slug)))
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
  def rename(author: String, slug: String): Action[String] =
    SettingsEditAction(author, slug).asyncEitherT(parse.form(forms.ProjectRename)) { implicit request =>
      val project = request.data.project
      val newName = compact(request.body)
      val oldName = request.project.name

      for {
        available <- EitherT.right[Result](projects.isNamespaceAvailable(author, slugify(newName)))
        _ <- EitherT
          .cond[IO](available, (), Redirect(self.showSettings(author, slug)).withError("error.nameUnavailable"))
        _ <- EitherT.right[Result](projects.rename(project, newName))
        _ <- EitherT.right[Result] {
          UserActionLogger.log(
            request.request,
            LoggedAction.ProjectRenamed,
            request.project.id,
            s"$author/$newName",
            s"$author/$oldName"
          )
        }
      } yield Redirect(self.show(author, project.slug))
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
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.Reviewer))
      .asyncF { implicit request =>
        val newVisibility = Visibility.withValue(visibility)
        val forumVisbility =
          if (!Visibility.isPublic(newVisibility) && Visibility.isPublic(request.project.visibility)) {
            this.forums.changeTopicVisibility(request.project, isVisible = false).void
          } else if (Visibility.isPublic(newVisibility) && !Visibility.isPublic(request.project.visibility)) {
            this.forums.changeTopicVisibility(request.project, isVisible = true).void
          } else IO.unit

        val projectVisibility = if (newVisibility.showModal) {
          val comment = this.forms.NeedsChanges.bindFromRequest.get.trim
          request.project.setVisibility(newVisibility, comment, request.user.id)
        } else {
          request.project.setVisibility(newVisibility, "", request.user.id)
        }

        val log = UserActionLogger.log(
          request.request,
          LoggedAction.ProjectVisibilityChange,
          request.project.id,
          newVisibility.nameKey,
          Visibility.NeedsChanges.nameKey
        )

        (forumVisbility, projectVisibility).parTupled
          .productR((log, projects.refreshHomePage(MDCLogger)).parTupled)
          .as(Ok)
      }
  }

  /**
    * Set a project that is in new to public
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def publish(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).asyncF {
    implicit request =>
      val effects =
        if (request.data.visibility == Visibility.New) {
          val visibility = request.project.setVisibility(Visibility.Public, "", request.user.id)
          val log = UserActionLogger.log(
            request.request,
            LoggedAction.ProjectVisibilityChange,
            request.project.id,
            Visibility.Public.nameKey,
            Visibility.New.nameKey
          )

          visibility *> log.void
        } else IO.unit

      effects.as(Redirect(self.show(request.project.ownerName, request.project.slug)))
  }

  /**
    * Set a project that needed changes to the approval state
    * @param author   Project owner
    * @param slug     Project slug
    * @return         Redirect home
    */
  def sendForApproval(author: String, slug: String): Action[AnyContent] = SettingsEditAction(author, slug).asyncF {
    implicit request =>
      val effects = if (request.data.visibility == Visibility.NeedsChanges) {
        val visibility = request.project.setVisibility(Visibility.NeedsApproval, "", request.user.id)
        val log = UserActionLogger.log(
          request.request,
          LoggedAction.ProjectVisibilityChange,
          request.project.id,
          Visibility.NeedsApproval.nameKey,
          Visibility.NeedsChanges.nameKey
        )

        visibility *> log.void
      } else IO.unit
      effects.as(Redirect(self.show(request.project.ownerName, request.project.slug)))
  }

  def showLog(author: String, slug: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction(Permission.ViewLogs)).andThen(ProjectAction(author, slug)).asyncF {
      implicit request =>
        for {
          logger <- request.project.logger
          logs   <- service.runDBIO(logger.entries(ModelView.raw(ProjectLogEntry)).result)
        } yield Ok(views.log(request.project, logs))
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
    Authenticated.andThen(PermissionAction(Permission.HardDeleteProject)).asyncF { implicit request =>
      getProject(author, slug).semiflatMap { project =>
        val effects = projects.delete(project) *>
          UserActionLogger.log(
            request,
            LoggedAction.ProjectVisibilityChange,
            project.id,
            "deleted",
            project.visibility.nameKey
          ) *>
          projects.refreshHomePage(MDCLogger)
        effects.as(Redirect(ShowHome).withSuccess(request.messages.apply("project.deleted", project.name)))
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
  def softDelete(author: String, slug: String): Action[String] =
    AuthedProjectAction(author, slug, requireUnlock = true)
      .andThen(ProjectPermissionAction(Permission.DeleteProject))
      .asyncF(parse.form(forms.NeedsChanges)) { implicit request =>
        val oldProject = request.project
        val comment    = request.body.trim

        val oreVisibility   = oldProject.setVisibility(Visibility.SoftDelete, comment, request.user.id)
        val forumVisibility = this.forums.changeTopicVisibility(oldProject, isVisible = false)
        val log = UserActionLogger.log(
          request.request,
          LoggedAction.ProjectVisibilityChange,
          oldProject.id,
          Visibility.SoftDelete.nameKey,
          oldProject.visibility.nameKey
        )

        (oreVisibility, forumVisibility).parTupled
          .productR((log, projects.refreshHomePage(MDCLogger)).parTupled)
          .as(Redirect(ShowHome).withSuccess(request.messages.apply("project.deleted", oldProject.name)))
      }

  /**
    * Show the flags that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showFlags(author: String, slug: String): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.ModNotesAndFlags)).andThen(ProjectAction(author, slug)) {
      implicit request =>
        Ok(views.admin.flags(request.data))
    }

  /**
    * Show the notes that have been made on this project
    *
    * @param author Project owner
    * @param slug   Project slug
    */
  def showNotes(author: String, slug: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction[AuthRequest](Permission.ModNotesAndFlags)).asyncEitherT { implicit request =>
      getProject(author, slug).semiflatMap { project =>
        import cats.instances.vector._
        project.decodeNotes.toVector.parTraverse(note => ModelView.now(User).get(note.user).value.tupleLeft(note)).map {
          notes =>
            Ok(views.admin.notes(project, Model.unwrapNested(notes)))
        }
      }
    }
  }

  def addMessage(author: String, slug: String): Action[String] = {
    Authenticated
      .andThen(PermissionAction[AuthRequest](Permission.ModNotesAndFlags))
      .asyncEitherT(parse.form(forms.NoteDescription)) { implicit request =>
        getProject(author, slug)
          .semiflatMap(_.addNote(Note(request.body.trim, request.user.id)))
          .map(_ => Ok("Review"))
      }
  }
}
