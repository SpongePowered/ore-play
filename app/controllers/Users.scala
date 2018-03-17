package controllers

import javax.inject.Inject

import controllers.sugar.Bakery
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.{ProjectTableMain, UserTable, VersionTable}
import db.impl.access.UserBase.ORDERING_PROJECTS
import discourse.OreDiscourseApi
import form.OreForms
import models.user.role.RoleModel
import models.user.{Notification, SignOn, User}
import ore.rest.OreWrites
import ore.user.notification.InviteFilters.InviteFilter
import ore.user.notification.NotificationFilters.NotificationFilter
import ore.user.notification.{InviteFilters, NotificationFilters}
import ore.user.{FakeUser, Prompts}
import ore.{OreConfig, OreEnv}
import mail.{EmailFactory, Mailer}
import models.project.{Project, Version}
import models.viewhelper.{HeaderData, OrganizationData, UserData}
import play.Logger
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc._
import security.spauth.SingleSignOnConsumer
import views.{html => views}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Controller for general user actions.
  */
class Users @Inject()(fakeUser: FakeUser,
                      forms: OreForms,
                      forums: OreDiscourseApi,
                      writes: OreWrites,
                      mailer: Mailer,
                      emails: EmailFactory,
                      implicit override val bakery: Bakery,
                      implicit override val sso: SingleSignOnConsumer,
                      implicit override val messagesApi: MessagesApi,
                      implicit override val env: OreEnv,
                      implicit override val config: OreConfig,
                      implicit override val cache: AsyncCacheApi,
                      implicit override val service: ModelService) extends OreBaseController {

  private val baseUrl = this.config.app.get[String]("baseUrl")

  /**
    * Redirect to auth page for SSO authentication.
    *
    * @return Logged in page
    */
  def signUp() = Action { implicit request =>
    val nonce = SingleSignOnConsumer.nonce
    this.signOns.add(SignOn(nonce = nonce))
    redirectToSso(this.sso.getSignupUrl(this.baseUrl + "/login", nonce))
  }

  /**
    * Redirect to auth page for SSO authentication and then back here again.
    *
    * @param sso  Incoming payload from auth
    * @param sig  Incoming signature from auth
    * @return     Logged in home
    */
  def logIn(sso: Option[String], sig: Option[String], returnPath: Option[String]) = Action.async { implicit request =>
    if (this.fakeUser.isEnabled) {
      // Log in as fake user (debug only)
      this.config.checkDebug()
      this.users.getOrCreate(this.fakeUser)
      this.redirectBack(returnPath.getOrElse(request.path), this.fakeUser)
    } else if (sso.isEmpty || sig.isEmpty) {
      val nonce = SingleSignOnConsumer.nonce
      this.signOns.add(SignOn(nonce = nonce))
      Future.successful(redirectToSso(this.sso.getLoginUrl(this.baseUrl + "/login", nonce)))
    } else {
      // Redirected from SpongeSSO, decode SSO payload and convert to Ore user
      this.sso.authenticate(sso.get, sig.get)(isNonceValid) flatMap {
        case None =>
          Future.successful(Redirect(ShowHome).withError("error.loginFailed"))
        case Some(spongeUser) =>
          // Complete authentication
          User.fromSponge(spongeUser).flatMap(this.users.getOrCreate).flatMap { user =>
            user.pullForumData.flatMap(_.pullSpongeData).flatMap { u =>
              this.redirectBack(request.flash.get("url").getOrElse("/"), u)
            }
          }
      }
    }
  }

  /**
    * Redirects the user to the auth verification page to re-enter their
    * password and then perform some action.
    *
    * @param returnPath Verified action to perform
    * @return           Redirect to verification
    */
  def verify(returnPath: Option[String]) = Authenticated { implicit request =>
    val nonce = SingleSignOnConsumer.nonce
    this.signOns.add(SignOn(nonce = nonce))
    redirectToSso(this.sso.getVerifyUrl(this.baseUrl + returnPath.getOrElse("/"), nonce))
  }

  private def redirectToSso(url: String): Result = {
    if (this.sso.isAvailable)
      Redirect(url)
    else
      Redirect(ShowHome).withError("error.noLogin")
  }

  private def redirectBack(url: String, user: User)
  = Redirect(this.baseUrl + url).authenticatedAs(user, this.config.play.get[Int]("http.session.maxAge"))

  /**
    * Clears the current session.
    *
    * @return Home page
    */
  def logOut(returnPath: Option[String]) = Action { implicit request =>
    Redirect(this.baseUrl + returnPath.getOrElse(request.path)).clearingSession().flashing("noRedirect" -> "true")
  }

  /**
    * Shows the User's [[models.project.Project]]s page for the user with the
    * specified username.
    *
    * @param username   Username to lookup
    * @return           View of user projects page
    */
  def showProjects(username: String, page: Option[Int]) = OreAction async { implicit request =>
    val pageSize = this.config.users.get[Int]("project-page-size")
    val p = page.getOrElse(1)
    val offset = (p - 1) * pageSize
    this.users.withName(username).flatMap {
      case None => Future.successful(NotFound)
      case Some(user) =>
        for {
          // TODO include orga projects?
          projectSeq <- service.DB.db.run(queryUserProjects(user).drop(offset).take(pageSize).result)
          tags <- Future.sequence(projectSeq.map(_._2.tags))
          userData <- getUserData(request, username)
          starred <- user.starred()
          starredRv <- Future.sequence(starred.map(_.recommendedVersion))
          orgaData <- getOrganizationData(request, username)
        } yield {
          val data = projectSeq zip tags map { case ((p, v), tags) =>
            (p, user, v, tags)
          }
          val starredData = starred zip starredRv
          Ok(views.users.projects(userData.get, orgaData, data, starredData, p))
        }
    }
  }

  private def queryUserProjects(user: User) = {
    queryProjectRV filter { case (p, v) =>
      p.userId === user.id.get
    } sortBy { case (p, v) =>
      (p.stars.desc, p.name.asc)
    }
  }

  private def queryProjectRV = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]

    for {
      p <- tableProject
      v <- tableVersion if p.recommendedVersionId === v.id
    } yield {
      (p, v)
    }
  }

  /**
    * Submits a change to the specified user's tagline.
    *
    * @param username   User to update
    * @return           View of user page
    */
  def saveTagline(username: String) = UserAction(username).async { implicit request =>
    val tagline = this.forms.UserTagline.bindFromRequest.get.trim
    val maxLen = this.config.users.get[Int]("max-tagline-len")
    this.users.withName(username).map {
      case None => NotFound
      case Some(user) =>
        if (tagline.length > maxLen) {
          Redirect(ShowUser(user)).flashing("error" -> this.messagesApi("error.tagline.tooLong", maxLen))
        } else {
          user.setTagline(tagline)
          Redirect(ShowUser(user))
        }
    }
  }

  /**
    * Attempts to save a submitted PGP Public Key to the specified User
    * profile.
    *
    * @param username User to save key to
    * @return JSON response
    */
  def savePgpPublicKey(username: String) = UserAction(username) { implicit request =>
    this.forms.UserPgpPubKey.bindFromRequest.fold(
      hasErrors =>
        Redirect(ShowUser(username)).withError(hasErrors.errors.head.message),
      keySubmission => {
        import writes._
        val keyInfo = keySubmission.info
        val user = request.user
        user.setPgpPubKey(keyInfo.raw)
        if (user.lastPgpPubKeyUpdate.isDefined)
          user.setLastPgpPubKeyUpdate(this.service.theTime) // Not set the first time

        // Send email notification
        this.mailer.push(this.emails.create(user, this.emails.PgpUpdated))

        Redirect(ShowUser(username)).flashing("pgp-updated" -> "true")
      }
    )
  }

  /**
    * Deletes the specified [[User]]'s PGP public key if it exists.
    *
    * @param username Username to delete key for
    * @return Ok if deleted, bad request if didn't exist
    */
  def deletePgpPublicKey(username: String, sso: Option[String], sig: Option[String]) = {
    VerifiedAction(username, sso, sig) { implicit request =>
      Logger.info("Deleting public key for " + username)
      val user = request.user
      if (user.pgpPubKey.isEmpty)
        BadRequest
      else {
        user.setPgpPubKey(null)
        user.setLastPgpPubKeyUpdate(this.service.theTime)
        Redirect(ShowUser(username)).flashing("pgp-updated" -> "true")
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
  def setLocked(username: String, locked: Boolean, sso: Option[String], sig: Option[String]) = {
    VerifiedAction(username, sso, sig) { implicit request =>
      val user = request.user
      user.setLocked(locked)
      if (!locked)
        this.mailer.push(this.emails.create(user, this.emails.AccountUnlocked))
      Redirect(ShowUser(username))
    }
  }

  /**
    * Shows a list of [[models.user.User]]s that have created a
    * [[models.project.Project]].
    */
  def showAuthors(sort: Option[String], page: Option[Int]) = OreAction async { implicit request =>
    val ordering = sort.getOrElse(ORDERING_PROJECTS)
    val p = page.getOrElse(1)
    this.users.getAuthors(ordering, p).map { u =>
      Ok(views.users.authors(u, ordering, p))
    }
  }

  /**
    * Displays the current user's unread notifications.
    *
    * @return Unread notifications
    */
  def showNotifications(notificationFilter: Option[String], inviteFilter: Option[String]) = {
    Authenticated.async { implicit request =>
      val user = request.user

      // Get visible notifications
      val nFilter: NotificationFilter = notificationFilter
        .map(str => NotificationFilters.values
          .find(_.name.equalsIgnoreCase(str))
          .getOrElse(NotificationFilters.Unread))
        .getOrElse(NotificationFilters.Unread)

      nFilter(user.notifications).flatMap { l =>
        Future.sequence(l.map(notif => notif.origin.map((notif, _))))
      } flatMap { notifications =>
        // Get visible invites
        val iFilter: InviteFilter = inviteFilter
          .map(str => InviteFilters.values
            .find(_.name.equalsIgnoreCase(str))
            .getOrElse(InviteFilters.All))
          .getOrElse(InviteFilters.All)

        iFilter(user).flatMap { invites =>
          Future.sequence(invites.map {invite => invite.subject.map((invite, _))})
        } map { invites =>
          Ok(views.users.notifications(
            notifications,
            invites,
            nFilter, iFilter))
        }
      }
    }
  }

  /**
    * Marks a [[models.user.User]]'s notification as read.
    *
    * @param id Notification ID
    * @return   Ok if marked as read, NotFound if notification does not exist
    */
  def markNotificationRead(id: Int) = Authenticated.async { implicit request =>
    request.user.notifications.get(id) map {
      case None => notFound
      case Some(notification) =>
        notification.setRead(read = true)
        Ok
    }
  }

  /**
    * Marks a [[ore.user.Prompts.Prompt]] as read for the authenticated
    * [[models.user.User]].
    *
    * @param id Prompt ID
    * @return   Ok if successful
    */
  def markPromptRead(id: Int) = Authenticated { implicit request =>
    Prompts.values.find(_.id == id) match {
      case None =>
        BadRequest
      case Some(prompt) =>
        request.user.markPromptRead(prompt)
        Ok
    }
  }

}
