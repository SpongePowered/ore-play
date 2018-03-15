package controllers.sugar

import java.util.Date

import controllers.routes
import controllers.sugar.Requests._
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.project.{Project, VisibilityTypes}
import models.user.{SignOn, User}
import ore.permission.scope.GlobalScope
import ore.permission.{EditPages, EditSettings, HideProjects, Permission}
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc.{request, _}
import security.spauth.SingleSignOnConsumer

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

/**
  * A set of actions used by Ore.
  */
trait Actions extends Calls with ActionHelpers {

  val users: UserBase
  val projects: ProjectBase
  val organizations: OrganizationBase
  val sso: SingleSignOnConsumer
  val signOns: ModelAccess[SignOn]
  val bakery: Bakery

  val PermsLogger = play.api.Logger("Permissions")

  val AuthTokenName = "_oretoken"


  /** Called when a [[User]] tries to make a request they do not have permission for */
  def onUnauthorized(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
    val noRedirect = request.flash.get("noRedirect")
    for {
      currentUser <- this.users.current
    }
      yield {
        if (noRedirect.isEmpty && currentUser.isEmpty)
          Redirect(routes.Users.logIn(None, None, Some(request.path)))
        else
          Redirect(ShowHome)
      }
  }

  /**
    * Action to perform a permission check for the current ScopedRequest and
    * given Permission.
    *
    * @param p Permission to check
    * @tparam R Type of ScopedRequest that is being checked
    * @return The ScopedRequest as an instance of R
    */
  def PermissionAction[R[_] <: ScopedRequest[_]](p: Permission)(implicit ec: ExecutionContext) = new ActionRefiner[ScopedRequest, R] {
    def executionContext = ec

    private def log(success: Boolean, request: ScopedRequest[_]) = {
      val lang = if (success) "GRANTED" else "DENIED"
      PermsLogger.info(s"<PERMISSION $lang> ${request.user.name}@${request.path.substring(1)}")
    }

    def refine[A](request: ScopedRequest[A]) = {
      implicit val r = request
      if (!(request.user can p in request.subject)) {
        log(success = false, request)
        onUnauthorized.map(Left(_))
      } else {
        log(success = true, request)
        Future.successful(Right(request.asInstanceOf[R[A]]))
      }
    }

  }

  /**
    * A PermissionAction that uses an AuthedProjectRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return An [[AuthedProjectRequest]]
    */
  def ProjectPermissionAction(p: Permission)(implicit ec: ExecutionContext) = PermissionAction[AuthedProjectRequest](p)

  /**
    * A PermissionAction that uses an AuthedOrganizationRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return [[AuthedOrganizationRequest]]
    */
  def OrganizationPermissionAction(p: Permission)(implicit ec: ExecutionContext) = PermissionAction[AuthedOrganizationRequest](p)

  implicit final class ResultWrapper(result: Result) {

    /**
      * Adds a new session cookie to the result for the specified [[User]].
      *
      * @param user   User to create session for
      * @param maxAge Maximum session age
      * @return Result with token
      */
    def authenticatedAs(user: User, maxAge: Int = -1)(implicit ec: ExecutionContext) = {
      val session = Actions.this.users.createSession(user)
      val age = if (maxAge == -1) None else Some(maxAge)
      session.map { s =>
        result.withCookies(Actions.this.bakery.bake(AuthTokenName, s.token, age))
      }
    }

    /**
      * Indicates that the client's session cookie should be cleared.
      *
      * @return
      */
    def clearingSession() = result.discardingCookies(DiscardingCookie(AuthTokenName))

  }

  /**
    * Returns true and marks the nonce as used if the specified nonce has not
    * been used, has not expired.
    *
    * @param nonce Nonce to check
    * @return True if valid
    */
  def isNonceValid(nonce: String)(implicit ec: ExecutionContext): Future[Boolean] = this.signOns.find(_.nonce === nonce).map {
    _.exists {
      signOn =>
        if (signOn.isCompleted || new Date().getTime - signOn.createdAt.get.getTime > 600000)
          false
        else {
          signOn.setCompleted()
          true
        }
    }
  }

  /**
    * Returns a NotFound result with the 404 HTML template.
    *
    * @return NotFound
    */
  def notFound()(implicit request: Request[_]): Result

  // Implementation

  def userLock(redirect: Call)(implicit ec: ExecutionContext) = new ActionFilter[AuthRequest] {
    def executionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
      if (request.user.isLocked)
        Some(Redirect(redirect).flashing("error" -> "error.user.locked"))
      else
        None
    }
  }

  def verifiedAction(sso: Option[String], sig: Option[String])(implicit ec: ExecutionContext) = new ActionFilter[AuthRequest] {
    def executionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] =
      if (sso.isEmpty || sig.isEmpty)
        Future.successful(Some(Unauthorized))
      else {
        Actions.this.sso.authenticate(sso.get, sig.get)(isNonceValid) map {
          case None => Some(Unauthorized)
          case Some(spongeUser) =>
            if (spongeUser.id == request.user.id.get)
              None
            else
              Some(Unauthorized)
        }
      }
  }

  def userAction(username: String)(implicit ec: ExecutionContext) = new ActionFilter[AuthRequest] {
    def executionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = {
      val user = request.user
      Actions.this.users.requestPermission(user, username, EditSettings).map {
        case None => Some(Unauthorized) // No Permission
        case Some(_) => None            // Permission granted => No Filter
      }
    }
  }

  def authAction(implicit ec: ExecutionContext) = new ActionRefiner[Request, AuthRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] =
      maybeAuthRequest(request, users.current(request, ec))

  }

  private def maybeAuthRequest[A](request: Request[A], futUser: Future[Option[User]])(implicit ec: ExecutionContext): Future[Either[Result, AuthRequest[A]]] = {
    futUser.flatMap {
      case None => onUnauthorized(request, ec).map(Left(_))
      case Some(user) => val as = Future.successful(Right(AuthRequest[A](user, request)))
        as
    }
  }

  def projectAction(author: String, slug: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, ProjectRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]) = maybeProjectRequest(request, Actions.this.projects.withSlug(author, slug))
  }

  def projectAction(pluginId: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, ProjectRequest[A]]]
    = maybeProjectRequest(request, Actions.this.projects.withPluginId(pluginId))
  }

  def maybeProjectRequest[A](request: Request[A], project: Future[Option[Project]])(implicit ec: ExecutionContext): Future[Either[Result, ProjectRequest[A]]] = {
    implicit val r = request
    val pr = project.flatMap {
      case None => Future.successful(None)
      case Some(p) => users.current.map(processProject(p, _))
    }
    pr.map {
      case None => Left(notFound())
      case Some(p) => Right(new ProjectRequest[A](p, request))
    }
  }

  def processProject(project: Project, user: Option[User]): Option[Project] = {
    if (project.visibility == VisibilityTypes.Public || project.visibility == VisibilityTypes.New
      || (user.isDefined && (user.get can EditPages in project)
      && (project.visibility == VisibilityTypes.NeedsChanges
      || project.visibility == VisibilityTypes.NeedsApproval))
      || (user.isDefined && (user.get can HideProjects in GlobalScope)
      ))
      Some(project)
    else
      None
  }

  def authedProjectActionImpl(project: Future[Option[Project]])(implicit ec: ExecutionContext) = new ActionRefiner[AuthRequest, AuthedProjectRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) = project.map { p =>
      p.flatMap(processProject(_, Some(request.user)))
        .map(new AuthedProjectRequest[A](_, request))
        .toRight(notFound()(request))
    }
  }

  def authedProjectAction(author: String, slug: String)(implicit ec: ExecutionContext)
  = authedProjectActionImpl(projects.withSlug(author, slug))

  def authedProjectActionById(pluginId: String)(implicit ec: ExecutionContext)
  = authedProjectActionImpl(projects.withPluginId(pluginId))

  def organizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, OrganizationRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, OrganizationRequest[A]]] =
      Actions.this.organizations.withName(organization).map(
        _.map(new OrganizationRequest(_, request)).toRight(notFound()(request)))
  }

  def authedOrganizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) =
      Actions.this.organizations.withName(organization).map(
        _.map(new AuthedOrganizationRequest[A](_, request))
        .toRight(notFound()(request)))
  }

}
