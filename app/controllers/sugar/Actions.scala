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
import play.api.mvc._
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
  def onUnauthorized(request: Request[_]) = {
    if (request.flash.get("noRedirect").isEmpty && this.users.current(request).isEmpty)
      Redirect(routes.Users.logIn(None, None, Some(request.path)))
    else
      Redirect(ShowHome)
  }

  /**
    * Action to perform a permission check for the current ScopedRequest and
    * given Permission.
    *
    * @param p  Permission to check
    * @tparam R Type of ScopedRequest that is being checked
    * @return   The ScopedRequest as an instance of R
    */
  def PermissionAction[R[_] <: ScopedRequest[_]](p: Permission)(implicit ec: ExecutionContext) = new ActionRefiner[ScopedRequest, R] {
    def executionContext = ec

    private def log(success: Boolean, request: ScopedRequest[_]) = {
      val lang = if (success) "GRANTED" else "DENIED"
      PermsLogger.info(s"<PERMISSION $lang> ${request.user.name}@${request.path.substring(1)}")
    }

    def refine[A](request: ScopedRequest[A]) = Future.successful {
      if (!(request.user can p in request.subject)) {
        log(success = false, request)
        Left(onUnauthorized(request))
      } else {
        log(success = true, request)
        Right(request.asInstanceOf[R[A]])
      }
    }
  }

  /**
    * A PermissionAction that uses an AuthedProjectRequest for the
    * ScopedRequest.
    *
    * @param p  Permission to check
    * @return   An [[AuthedProjectRequest]]
    */
  def ProjectPermissionAction(p: Permission)(implicit ec: ExecutionContext) = PermissionAction[AuthedProjectRequest](p)

  /**
    * A PermissionAction that uses an AuthedOrganizationRequest for the
    * ScopedRequest.
    *
    * @param p  Permission to check
    * @return   [[AuthedOrganizationRequest]]
    */
  def OrganizationPermissionAction(p: Permission)(implicit ec: ExecutionContext) = PermissionAction[AuthedOrganizationRequest](p)

  implicit final class ResultWrapper(result: Result) {

    /**
      * Adds a new session cookie to the result for the specified [[User]].
      *
      * @param user   User to create session for
      * @param maxAge Maximum session age
      * @return       Result with token
      */
    def authenticatedAs(user: User, maxAge: Int = -1) = {
      val session = Actions.this.users.createSession(user)
      val age = if (maxAge == -1) None else Some(maxAge)
      result.withCookies(Actions.this.bakery.bake(AuthTokenName, session.token, age))
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
    * been used, has not exired.
    *
    * @param nonce  Nonce to check
    * @return       True if valid
    */
  def isNonceValid(nonce: String): Boolean = this.signOns.find(_.nonce === nonce).exists { signOn =>
    if (signOn.isCompleted || new Date().getTime - signOn.createdAt.get.getTime > 600000)
      false
    else {
      signOn.setCompleted()
      true
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

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
      if (sso.isEmpty || sig.isEmpty)
        Some(Unauthorized)
      else {
        Actions.this.sso.authenticate(sso.get, sig.get)(isNonceValid) match {
          case None =>
            Some(Unauthorized)
          case Some(spongeUser) =>
            if (spongeUser.id == request.user.id.get)
              None
            else
              Some(Unauthorized)
        }
      }
    }
  }

  def userAction(username: String)(implicit ec: ExecutionContext) = new ActionFilter[AuthRequest] {
    def executionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
      Actions.this.users.withName(username).flatMap[User] { toCheck =>
        val user = request.user
        if (user.equals(toCheck) || (toCheck.isOrganization && (user can EditSettings in toCheck.toOrganization)))
          Some(toCheck)
        else
          None
      }.map[Option[Result]](user => None).getOrElse(Some(Unauthorized))
    }
  }

  def authAction(implicit ec: ExecutionContext) = new ActionRefiner[Request, AuthRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] = Future.successful {
      users.current(request)
        .map(AuthRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

  def projectAction(author: String, slug: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, ProjectRequest] {
    def executionContext = ec

    def refine[A](request: Request[A])
    = Future.successful(maybeProjectRequest(request, Actions.this.projects.withSlug(author, slug)))
  }

  def projectAction(pluginId: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, ProjectRequest[A]]]
    = Future.successful(maybeProjectRequest(request, Actions.this.projects.withPluginId(pluginId)))
  }

  def maybeProjectRequest[A](request: Request[A], project: Option[Project]) = {
    project
      .flatMap(processProject(_, this.users.current(request)))
      .map(new ProjectRequest[A](_, request))
      .toRight(notFound()(request))
  }

  def processProject(project: Project, user: Option[User]): Option[Project] = {
    if (project.visibility == VisibilityTypes.Public || project.visibility == VisibilityTypes.New
      || (user.isDefined && (user.get can EditPages in project)
        && (project.visibility == VisibilityTypes.NeedsChanges
          || project.visibility == VisibilityTypes.NeedsApproval ))
      || (user.isDefined && (user.get can HideProjects in GlobalScope)
      ))
      Some(project)
    else
      None
  }

  def authedProjectActionImpl(project: Option[Project])(implicit ec: ExecutionContext) = new ActionRefiner[AuthRequest, AuthedProjectRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) = Future.successful {
      project.flatMap(processProject(_, Some(request.user)))
        .map(new AuthedProjectRequest[A](_, request))
        .toRight(notFound()(request))
    }
  }

  def authedProjectAction(author: String, slug: String)(implicit ec: ExecutionContext) = authedProjectActionImpl(this.projects.withSlug(author, slug))

  def authedProjectActionById(pluginId: String)(implicit ec: ExecutionContext) = authedProjectActionImpl(this.projects.withPluginId(pluginId))

  def organizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[Request, OrganizationRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, OrganizationRequest[A]]] = Future.successful {
      Actions.this.organizations.withName(organization)
        .map(new OrganizationRequest(_, request))
        .toRight(notFound()(request))
    }
  }

  def authedOrganizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) = Future.successful {
      Actions.this.organizations.withName(organization)
        .map(new AuthedOrganizationRequest[A](_, request))
        .toRight(notFound()(request))
    }
  }

}
