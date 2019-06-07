package controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.MessagesApi
import play.api.mvc._

import db.impl.access.UserBase.UserOrdering
import db.impl.query.UserPagesQueries
import form.OreForms
import mail.{EmailFactory, Mailer}
import models.viewhelper.{OrganizationData, ScopedOrganizationData, UserData}
import ore.OreEnv
import ore.data.Prompt
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.query.UserQueries
import ore.db.impl.schema.{ApiKeyTable, UserTable}
import ore.db.{DbRef, Model}
import ore.models.project.ProjectSortingStrategy
import ore.models.project.io.ProjectFiles
import ore.models.user.{FakeUser, _}
import ore.models.user.notification.{InviteFilter, NotificationFilter}
import ore.permission.Permission
import ore.permission.role.Role
import util.UserActionLogger
import util.syntax._
import views.{html => views}

import cats.data.EitherT
import cats.effect.{IO, Timer}
import cats.instances.list._
import cats.instances.option._
import cats.syntax.all._

/**
  * Controller for general user actions.
  */
@Singleton
class Users @Inject()(
    fakeUser: FakeUser,
    forms: OreForms,
    mailer: Mailer,
    emails: EmailFactory
)(
    implicit oreComponents: OreControllerComponents[IO],
    projectFiles: ProjectFiles,
    messagesApi: MessagesApi,
) extends OreBaseController {

  private val baseUrl = this.config.app.baseUrl

  /**
    * Redirect to auth page for SSO authentication.
    *
    * @return Logged in page
    */
  def signUp(): Action[AnyContent] = Action.asyncF {
    val nonce = sso.nonce()
    service.insert(SignOn(nonce = nonce)) *> redirectToSso(
      this.sso.getSignupUrl(this.baseUrl + "/login", nonce)
    )
  }

  /**
    * Redirect to auth page for SSO authentication and then back here again.
    *
    * @param sso  Incoming payload from auth
    * @param sig  Incoming signature from auth
    * @return     Logged in home
    */
  def logIn(sso: Option[String], sig: Option[String], returnPath: Option[String]): Action[AnyContent] = Action.asyncF {
    implicit request =>
      if (this.fakeUser.isEnabled) {
        // Log in as fake user (debug only)
        this.config.checkDebug()
        users
          .getOrCreate(
            this.fakeUser.username,
            this.fakeUser,
            ifInsert = fakeUser => fakeUser.globalRoles.addAssoc(Role.OreAdmin.toDbRole.id).void
          )
          .flatMap(fakeUser => this.redirectBack(returnPath.getOrElse(request.path), fakeUser))
      } else if (sso.isEmpty || sig.isEmpty) {
        val nonce = this.sso.nonce()
        service.insert(SignOn(nonce = nonce)) *> redirectToSso(
          this.sso.getLoginUrl(this.baseUrl + "/login", nonce)
        ).map(_.flashing("url" -> returnPath.getOrElse(request.path)))
      } else {
        // Redirected from SpongeSSO, decode SSO payload and convert to Ore user
        this.sso
          .authenticate(sso.get, sig.get)(isNonceValid)
          .map(sponge => sponge.toUser -> sponge)
          .semiflatMap {
            case (fromSponge, sponge) =>
              // Complete authentication
              for {
                user <- users.getOrCreate(sponge.username, fromSponge, _ => IO.unit)
                _    <- user.globalRoles.deleteAllFromParent
                _ <- sponge.newGlobalRoles
                  .fold(IO.unit)(_.map(_.toDbRole.id).traverse_(user.globalRoles.addAssoc(_)))
                result <- this.redirectBack(request.flash.get("url").getOrElse("/"), user)
              } yield result
          }
          .getOrElse(Redirect(ShowHome).withError("error.loginFailed"))
      }
  }

  /**
    * Redirects the user to the auth verification page to re-enter their
    * password and then perform some action.
    *
    * @param returnPath Verified action to perform
    * @return           Redirect to verification
    */
  def verify(returnPath: Option[String]): Action[AnyContent] = Authenticated.asyncF {
    val nonce = sso.nonce()
    service.insert(SignOn(nonce = nonce)) *> redirectToSso(
      this.sso.getVerifyUrl(this.baseUrl + returnPath.getOrElse("/"), nonce)
    )
  }

  private def redirectToSso(url: String): IO[Result] =
    this.sso.isAvailable.ifM(IO.pure(Redirect(url)), IO.pure(Redirect(ShowHome).withError("error.noLogin")))

  private def redirectBack(url: String, user: User) =
    Redirect(this.baseUrl + url).authenticatedAs(user, this.config.play.sessionMaxAge.toSeconds.toInt)

  /**
    * Clears the current session.
    *
    * @return Home page
    */
  def logOut(): Action[AnyContent] = Action {
    Redirect(config.security.api.url + "/accounts/logout/")
      .clearingSession()
      .flashing("noRedirect" -> "true")
  }

  /**
    * Shows the User's [[ore.models.project.Project]]s page for the user with the
    * specified username.
    *
    * @param username   Username to lookup
    * @return           View of user projects page
    */
  def showProjects(username: String, page: Option[Int]): Action[AnyContent] = OreAction.asyncF { implicit request =>
    val pageSize = this.config.ore.users.projectPageSize
    val pageNum  = page.getOrElse(1)
    val offset   = (pageNum - 1) * pageSize

    val canHideProjects = request.headerData.globalPerm(Permission.SeeHidden)

    users
      .withName(username)
      .semiflatMap { user =>
        for {
          // TODO include orga projects?
          t1 <- (
            service.runDbCon(
              UserPagesQueries
                .getProjects(
                  username,
                  request.headerData.currentUser.map(_.id.value),
                  canHideProjects,
                  ProjectSortingStrategy.MostStars,
                  pageSize,
                  offset
                )
                .to[Vector]
            ),
            getOrga(username).value,
            getUserData(request, username).value
          ).parTupled
          (projects, orga, userData) = t1
          t2 <- (
            OrganizationData.of(orga).value,
            ScopedOrganizationData.of(request.currentUser, orga).value
          ).parTupled
          (orgaData, scopedOrgaData) = t2
        } yield {
          Ok(
            views.users.projects(
              userData.get,
              orgaData.flatMap(a => scopedOrgaData.map(b => (a, b))),
              projects,
              pageNum
            )
          )
        }
      }
      .getOrElse(notFound)
  }

  /**
    * Submits a change to the specified user's tagline.
    *
    * @param username   User to update
    * @return           View of user page
    */
  def saveTagline(username: String): Action[String] =
    UserEditAction(username).asyncEitherT(parse.form(forms.UserTagline)) { implicit request =>
      val maxLen = this.config.ore.users.maxTaglineLen

      for {
        user <- users.withName(username).toRight(NotFound)
        res <- {
          val tagline = request.body
          if (tagline.length > maxLen)
            EitherT.rightT[IO, Result](
              Redirect(ShowUser(user)).withError(request.messages.apply("error.tagline.tooLong", maxLen))
            )
          else {
            val log = UserActionLogger
              .log(request, LoggedAction.UserTaglineChanged, user.id, tagline, user.tagline.getOrElse("null"))
            val insert = service.update(user)(_.copy(tagline = Some(tagline)))
            EitherT.right[Result]((log *> insert).as(Redirect(ShowUser(user))))
          }
        }
      } yield res
    }

  /**
    * Sets the "locked" status of a User.
    *
    * @param username User to set status of
    * @param locked   True if user is locked
    * @return         Redirection to user page
    */
  def setLocked(username: String, locked: Boolean, sso: Option[String], sig: Option[String]): Action[AnyContent] = {
    VerifiedAction(username, sso, sig).asyncF { implicit request =>
      val user = request.user
      if (!locked)
        this.mailer.push(this.emails.create(user, this.emails.AccountUnlocked))
      service
        .update(user)(_.copy(isLocked = locked))
        .as(Redirect(ShowUser(username)))
    }
  }

  /**
    * Shows a list of [[ore.models.user.User]]s that have created a
    * [[ore.models.project.Project]].
    */
  def showAuthors(sort: Option[String], page: Option[Int]): Action[AnyContent] = OreAction.asyncF { implicit request =>
    val ordering = sort.getOrElse(UserOrdering.Projects)
    val p        = page.getOrElse(1)

    service.runDbCon(UserPagesQueries.getAuthors(p, ordering).to[Vector]).map { u =>
      Ok(views.users.authors(u, ordering, p))
    }
  }

  /**
    * Shows a list of [[ore.models.user.User]]s that have Ore staff roles.
    */
  def showStaff(sort: Option[String], page: Option[Int]): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.IsStaff)).asyncF { implicit request =>
      val ordering = sort.getOrElse(UserOrdering.Role)
      val p        = page.getOrElse(1)

      service.runDbCon(UserPagesQueries.getStaff(p, ordering).to[Vector]).map { u =>
        Ok(views.users.staff(u, ordering, p))
      }
    }

  /**
    * Displays the current user's unread notifications.
    *
    * @return Unread notifications
    */
  def showNotifications(notificationFilter: Option[String], inviteFilter: Option[String]): Action[AnyContent] = {
    Authenticated.asyncF { implicit request =>
      import cats.instances.vector._
      val user = request.user

      // Get visible notifications
      val nFilter: NotificationFilter = notificationFilter
        .flatMap(str => NotificationFilter.values.find(_.name.equalsIgnoreCase(str)))
        .getOrElse(NotificationFilter.Unread)

      val iFilter: InviteFilter = inviteFilter
        .flatMap(str => InviteFilter.values.find(_.name.equalsIgnoreCase(str)))
        .getOrElse(InviteFilter.All)

      val notificationsF = service.runDBIO(
        nFilter(user.notifications(ModelView.raw(Notification)))
          .joinLeft(TableQuery[UserTable])
          .on(_.originId === _.id)
          .result
      )
      val invitesF = iFilter(user).flatMap(i => i.toVector.parTraverse(invite => invite.subject.tupleLeft(invite)))

      (notificationsF, invitesF).parMapN { (notifications, invites) =>
        Ok(
          views.users.notifications(
            Model.unwrapNested[Seq[(Model[Notification], Option[User])]](notifications),
            invites.map(t => t._1 -> t._2.obj),
            nFilter,
            iFilter
          )
        )
      }
    }
  }

  /**
    * Marks a [[ore.models.user.User]]'s notification as read.
    *
    * @param id Notification ID
    * @return   Ok if marked as read, NotFound if notification does not exist
    */
  def markNotificationRead(id: DbRef[Notification]): Action[AnyContent] = Authenticated.asyncF { implicit request =>
    request.user
      .notifications(ModelView.now(Notification))
      .get(id)
      .semiflatMap(notification => service.update(notification)(_.copy(isRead = true)).as(Ok))
      .getOrElse(notFound)
  }

  /**
    * Marks a [[Prompt]] as read for the authenticated
    * [[ore.models.user.User]].
    *
    * @param id Prompt ID
    * @return   Ok if successful
    */
  def markPromptRead(id: Int): Action[AnyContent] = Authenticated.asyncF { implicit request =>
    Prompt.values.find(_.value == id) match {
      case None         => IO.pure(BadRequest)
      case Some(prompt) => request.user.markPromptAsRead(prompt).as(Ok)
    }
  }

  def editApiKeys(username: String): Action[AnyContent] =
    Authenticated.asyncF { implicit request =>
      if (request.user.name == username) {
        for {
          t1 <- (
            getOrga(username).value,
            UserData.of(request, request.user),
            service.runDBIO(TableQuery[ApiKeyTable].filter(_.ownerId === request.user.id.value).result)
          ).parTupled
          (orga, userData, keys) = t1
          t2 <- (
            OrganizationData.of(orga).value,
            ScopedOrganizationData.of(request.currentUser, orga).value
          ).parTupled
          (orgaData, scopedOrgaData) = t2
          totalPerms <- (
            service.runDbCon(UserQueries.allPossibleProjectPermissions(request.user.id).unique),
            service.runDbCon(UserQueries.allPossibleOrgPermissions(request.user.id).unique)
          ).parMapN(_.add(_).add(userData.userPerm))
        } yield
          Ok(
            views.users.apiKeys(
              userData,
              orgaData.flatMap(a => scopedOrgaData.map(b => (a, b))),
              Model.unwrapNested(keys),
              totalPerms.toNamedSeq
            )
          )
      } else IO.pure(Forbidden)
    }
}
