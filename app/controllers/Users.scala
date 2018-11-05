package controllers

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.Logger
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc._

import controllers.sugar.Bakery
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase.UserOrdering
import db.impl.schema.{ProjectTableMain, VersionTable}
import db.query.UserQueries
import db.{ModelService, ObjectReference}
import form.{OreForms, PGPPublicKeySubmission}
import mail.{EmailFactory, Mailer}
import models.user.{LoggedAction, SignOn, User, UserActionLogger}
import models.viewhelper.{OrganizationData, ScopedOrganizationData}
import ore.permission.ReviewProjects
import ore.permission.role.Role
import ore.user.notification.{InviteFilter, NotificationFilter}
import ore.user.{FakeUser, Prompt}
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.data.EitherT
import cats.instances.future._
import cats.instances.list._
import cats.syntax.all._

/**
  * Controller for general user actions.
  */
class Users @Inject()(
    fakeUser: FakeUser,
    forms: OreForms,
    mailer: Mailer,
    emails: EmailFactory
)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    messagesApi: MessagesApi,
    env: OreEnv,
    config: OreConfig,
    cache: AsyncCacheApi,
    service: ModelService
) extends OreBaseController {

  private val baseUrl = this.config.app.get[String]("baseUrl")

  /**
    * Redirect to auth page for SSO authentication.
    *
    * @return Logged in page
    */
  def signUp(): Action[AnyContent] = Action.async { implicit request =>
    val nonce = SingleSignOnConsumer.nonce
    this.signOns.add(SignOn(nonce = nonce)).as(redirectToSso(this.sso.getSignupUrl(this.baseUrl + "/login", nonce)))
  }

  /**
    * Redirect to auth page for SSO authentication and then back here again.
    *
    * @param sso  Incoming payload from auth
    * @param sig  Incoming signature from auth
    * @return     Logged in home
    */
  def logIn(sso: Option[String], sig: Option[String], returnPath: Option[String]): Action[AnyContent] = Action.async {
    implicit request =>
      if (this.fakeUser.isEnabled) {
        // Log in as fake user (debug only)
        this.config.checkDebug()
        users.getOrCreate(this.fakeUser) *>
          this.fakeUser.globalRoles
            .contains(Role.OreAdmin.toDbRole)
            .ifM(ifTrue = Future.unit, ifFalse = this.fakeUser.globalRoles.add(Role.OreAdmin.toDbRole).void) *>
          this.redirectBack(returnPath.getOrElse(request.path), this.fakeUser)
      } else if (sso.isEmpty || sig.isEmpty) {
        val nonce = SingleSignOnConsumer.nonce
        this.signOns.add(SignOn(nonce = nonce)).as(redirectToSso(this.sso.getLoginUrl(this.baseUrl + "/login", nonce)))
      } else {
        // Redirected from SpongeSSO, decode SSO payload and convert to Ore user
        this.sso
          .authenticate(sso.get, sig.get)(isNonceValid)
          .map(sponge => User.fromSponge(sponge) -> sponge)
          .semiflatMap {
            case (fromSponge, sponge) =>
              // Complete authentication
              for {
                user   <- users.getOrCreate(fromSponge)
                _      <- user.globalRoles.removeAll()
                _      <- sponge.newGlobalRoles.fold(Future.unit)(_.map(_.toDbRole).traverse_(user.globalRoles.add))
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
  def verify(returnPath: Option[String]): Action[AnyContent] = Authenticated.async { implicit request =>
    val nonce = SingleSignOnConsumer.nonce
    this.signOns
      .add(SignOn(nonce = nonce))
      .as(redirectToSso(this.sso.getVerifyUrl(this.baseUrl + returnPath.getOrElse("/"), nonce)))
  }

  private def redirectToSso(url: String): Result =
    if (this.sso.isAvailable)
      Redirect(url)
    else
      Redirect(ShowHome).withError("error.noLogin")

  private def redirectBack(url: String, user: User) =
    Redirect(this.baseUrl + url).authenticatedAs(user, this.config.play.get[Int]("http.session.maxAge"))

  /**
    * Clears the current session.
    *
    * @return Home page
    */
  def logOut() = Action { implicit request =>
    Redirect(config.security.get[String]("api.url") + "/accounts/logout/")
      .clearingSession()
      .flashing("noRedirect" -> "true")
  }

  /**
    * Shows the User's [[models.project.Project]]s page for the user with the
    * specified username.
    *
    * @param username   Username to lookup
    * @return           View of user projects page
    */
  def showProjects(username: String, page: Option[Int]): Action[AnyContent] = OreAction.async { implicit request =>
    val pageSize = this.config.users.get[Int]("project-page-size")
    val pageNum  = page.getOrElse(1)
    val offset   = (pageNum - 1) * pageSize
    users
      .withName(username)
      .semiflatMap { user =>
        for {
          // TODO include orga projects?
          (projectSeq, starred, orga) <- (
            service.runDBIO(queryUserProjects(user).drop(offset).take(pageSize).result),
            user.starred(),
            getOrga(username).value
          ).tupled
          (tagsSeq, userData, starredRv, orgaData, scopedOrgaData) <- (
            Future.sequence(projectSeq.map(_._2.tags)),
            getUserData(request, username).value,
            Future.sequence(starred.map(_.recommendedVersion)),
            OrganizationData.of(orga).value,
            ScopedOrganizationData.of(request.currentUser, orga).value
          ).tupled
        } yield {
          val data = projectSeq.zip(tagsSeq).map {
            case ((p, v), tags) => (p, user, v, tags)
          }
          val starredData = starred.zip(starredRv)
          Ok(
            views.users.projects(
              userData.get,
              orgaData.flatMap(a => scopedOrgaData.map(b => (a, b))),
              data,
              starredData.take(5),
              pageNum
            )
          )
        }
      }
      .getOrElse(notFound)
  }

  private def queryUserProjects(user: User) =
    queryProjectRV
      .filter { case (p, _) => p.userId === user.id.value }
      .sortBy { case (p, _) => (p.stars.desc, p.name.asc) }

  private def queryProjectRV =
    for {
      p <- TableQuery[ProjectTableMain]
      v <- TableQuery[VersionTable] if p.recommendedVersionId === v.id
    } yield (p, v)

  /**
    * Submits a change to the specified user's tagline.
    *
    * @param username   User to update
    * @return           View of user page
    */
  def saveTagline(username: String): Action[String] =
    UserAction(username).asyncEitherT(parse.form(forms.UserTagline)) { implicit request =>
      val maxLen = this.config.users.get[Int]("max-tagline-len")

      for {
        user <- users.withName(username).toRight(NotFound)
        res <- {
          val tagline = request.body
          if (tagline.length > maxLen)
            EitherT.rightT[Future, Result](
              Redirect(ShowUser(user)).withError(request.messages.apply("error.tagline.tooLong", maxLen))
            )
          else {
            val log = UserActionLogger
              .log(request, LoggedAction.UserTaglineChanged, user.id.value, tagline, user.tagline.getOrElse("null"))
            val insert = service.update(user.copy(tagline = Some(tagline)))
            EitherT.right[Result]((log *> insert).as(Redirect(ShowUser(user))))
          }
        }
      } yield res
    }

  /**
    * Attempts to save a submitted PGP Public Key to the specified User
    * profile.
    *
    * @param username User to save key to
    * @return JSON response
    */
  def savePgpPublicKey(username: String): Action[PGPPublicKeySubmission] =
    UserAction(username).async(parse.form(forms.UserPgpPubKey, onErrors = FormError(ShowUser(username)))) {
      implicit request =>
        val keyInfo = request.body.info
        val user    = request.user

        // Send email notification
        this.mailer.push(this.emails.create(user, this.emails.PgpUpdated))
        val log = UserActionLogger.log(request, LoggedAction.UserPgpKeySaved, user.id.value, "", "")

        val update = service.update(
          user.copy(
            pgpPubKey = Some(keyInfo.raw),
            lastPgpPubKeyUpdate =
              if (user.lastPgpPubKeyUpdate.isDefined) Some(service.theTime)
              else user.lastPgpPubKeyUpdate
          )
        )

        (log *> update).as(Redirect(ShowUser(username)).flashing("pgp-updated" -> "true"))
    }

  /**
    * Deletes the specified [[User]]'s PGP public key if it exists.
    *
    * @param username Username to delete key for
    * @return Ok if deleted, bad request if didn't exist
    */
  def deletePgpPublicKey(username: String, sso: Option[String], sig: Option[String]): Action[AnyContent] = {
    VerifiedAction(username, sso, sig).async { implicit request =>
      Logger.debug("Deleting public key for " + username)
      val user = request.user
      if (user.pgpPubKey.isEmpty)
        Future.successful(BadRequest)
      else {
        val log = UserActionLogger.log(request, LoggedAction.UserPgpKeyRemoved, user.id.value, "", "")
        val insert = service.update(
          user.copy(
            pgpPubKey = None,
            lastPgpPubKeyUpdate = Some(service.theTime)
          )
        )

        (log *> insert).as(Redirect(ShowUser(username)).flashing("pgp-updated" -> "true"))
      }
    }
  }

  /**
    * Sets the "locked" status of a User.
    *
    * @param username User to set status of
    * @param locked   True if user is locked
    * @return         Redirection to user page
    */
  def setLocked(username: String, locked: Boolean, sso: Option[String], sig: Option[String]): Action[AnyContent] = {
    VerifiedAction(username, sso, sig).async { implicit request =>
      val user = request.user
      if (!locked)
        this.mailer.push(this.emails.create(user, this.emails.AccountUnlocked))
      service
        .update(user.copy(isLocked = locked))
        .as(Redirect(ShowUser(username)))
    }
  }

  /**
    * Shows a list of [[models.user.User]]s that have created a
    * [[models.project.Project]].
    */
  def showAuthors(sort: Option[String], page: Option[Int]): Action[AnyContent] = OreAction.async { implicit request =>
    val ordering = sort.getOrElse(UserOrdering.Projects)
    val p        = page.getOrElse(1)

    service.runDbCon(UserQueries.getAuthors(p, ordering).to[Vector]).map { u =>
      Ok(views.users.authors(u, ordering, p))
    }
  }

  /**
    * Shows a list of [[models.user.User]]s that have Ore staff roles.
    */
  def showStaff(sort: Option[String], page: Option[Int]): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).async { implicit request =>
      val ordering = sort.getOrElse(UserOrdering.Role)
      val p        = page.getOrElse(1)

      service.runDbCon(UserQueries.getStaff(p, ordering).to[Vector]).map { u =>
        Ok(views.users.staff(u, ordering, p))
      }
    }

  /**
    * Displays the current user's unread notifications.
    *
    * @return Unread notifications
    */
  def showNotifications(notificationFilter: Option[String], inviteFilter: Option[String]): Action[AnyContent] = {
    Authenticated.async { implicit request =>
      val user = request.user

      // Get visible notifications
      val nFilter: NotificationFilter = notificationFilter
        .flatMap(str => NotificationFilter.values.find(_.name.equalsIgnoreCase(str)))
        .getOrElse(NotificationFilter.Unread)

      val iFilter: InviteFilter = inviteFilter
        .flatMap(str => InviteFilter.values.find(_.name.equalsIgnoreCase(str)))
        .getOrElse(InviteFilter.All)

      val notificationsFut =
        nFilter(user.notifications).flatMap(l => Future.traverse(l)(notif => notif.origin.tupleLeft(notif)))
      val invitesFut = iFilter(user).flatMap(i => Future.traverse(i)(invite => invite.subject.tupleLeft(invite)))

      (notificationsFut, invitesFut).mapN { (notifications, invites) =>
        Ok(views.users.notifications(notifications, invites, nFilter, iFilter))
      }
    }
  }

  /**
    * Marks a [[models.user.User]]'s notification as read.
    *
    * @param id Notification ID
    * @return   Ok if marked as read, NotFound if notification does not exist
    */
  def markNotificationRead(id: ObjectReference): Action[AnyContent] = Authenticated.async { implicit request =>
    request.user.notifications
      .get(id)
      .semiflatMap(notification => service.update(notification.copy(isRead = true)).as(Ok))
      .getOrElse(notFound)
  }

  /**
    * Marks a [[ore.user.Prompt]] as read for the authenticated
    * [[models.user.User]].
    *
    * @param id Prompt ID
    * @return   Ok if successful
    */
  def markPromptRead(id: ObjectReference): Action[AnyContent] = Authenticated.async { implicit request =>
    Prompt.values.find(_.value == id) match {
      case None         => Future.successful(BadRequest)
      case Some(prompt) => request.user.markPromptAsRead(prompt).as(Ok)
    }
  }

}
