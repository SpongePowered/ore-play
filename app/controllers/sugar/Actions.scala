package controllers.sugar

import java.util.Date

import controllers.routes
import controllers.sugar.Requests._
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.project.{Project, VisibilityTypes}
import models.user.{SignOn, User}
import models.viewhelper.{HeaderData, OrganizationData, ProjectData, UserData}
import ore.permission.scope.GlobalScope
import ore.permission.{EditPages, EditSettings, HideProjects, Permission}
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc.{request, _}
import controllers.sugar.Requests._
import controllers.sugar.Requests
import play.api.cache.AsyncCacheApi
import security.spauth.SingleSignOnConsumer
import slick.jdbc.JdbcBackend

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

      request.user can p in request.subject flatMap { perm =>
        if (!perm) {
          log(success = false, request)
          onUnauthorized.map(Left(_))
        } else {
          log(success = true, request)
          Future.successful(Right(request.asInstanceOf[R[A]]))
        }
      }

    }

  }

  /**
    * A PermissionAction that uses an AuthedProjectRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return An [[ProjectRequest]]
    */
  def ProjectPermissionAction(p: Permission)(implicit ec: ExecutionContext) = PermissionAction[AuthedProjectRequest](p)

  /**
    * A PermissionAction that uses an AuthedOrganizationRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return [[OrganizationRequest]]
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
  def notFound()(implicit request: OreRequest[_]): Result

  // Implementation

  def userLock(redirect: Call)(implicit ec: ExecutionContext) = new ActionFilter[AuthRequest] {
    def executionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
      if (!request.user.isLocked) None
      else Some(Redirect(redirect).flashing("error" -> "error.user.locked"))
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
      Actions.this.users.requestPermission(request.user, username, EditSettings).map {
        case None => Some(Unauthorized) // No Permission
        case Some(_) => None            // Permission granted => No Filter
      }
    }
  }

  def oreAction(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = new ActionTransformer[Request, OreRequest] {
    def executionContext = ec

    def transform[A](request: Request[A]): Future[OreRequest[A]] = {
      implicit val service = users.service
      HeaderData.of(request).map(new OreRequest(_, request))
    }
  }

  def authAction(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = new ActionRefiner[Request, AuthRequest] {
    def executionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] =
      maybeAuthRequest(request, users.current(request, ec))

  }

  private def maybeAuthRequest[A](request: Request[A], futUser: Future[Option[User]])(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi,db: JdbcBackend#DatabaseDef): Future[Either[Result, AuthRequest[A]]] = {
    futUser.flatMap {
      case None => onUnauthorized(request, ec).map(Left(_))
      case Some(user) => {
        implicit val service = users.service
        HeaderData.of(request).map(hd => Right(new AuthRequest[A](user, hd, request)))
      }
    }
  }

  def projectAction(author: String, slug: String)(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext = ec

    def refine[A](request: OreRequest[A]) = maybeProjectRequest(request, Actions.this.projects.withSlug(author, slug))
  }

  def projectAction(pluginId: String)(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]) = maybeProjectRequest(request, Actions.this.projects.withPluginId(pluginId))
  }

  def maybeProjectRequest[A](r: OreRequest[A], project: Future[Option[Project]])(implicit asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[Either[Result, ProjectRequest[A]]] = {
    implicit val request = r
    val pr = project.flatMap {
      case None => Future.successful(None)
      case Some(p) => processProject(p, request.data.currentUser)
    }
    pr.flatMap {
      case None => Future.successful(Left(notFound()))
      case Some(p) => {
        ProjectData.of(request, p).map { pd =>
          Right(new ProjectRequest[A](pd, r))
        }
      }
    }
  }

  def processProject(project: Project, user: Option[User])(implicit ec: ExecutionContext) : Future[Option[Project]] = {
    if (project.visibility == VisibilityTypes.Public || project.visibility == VisibilityTypes.New) {
      Future.successful(Some(project))
    } else {
      if (user.isDefined) {
        for {
          check1 <- canEditAndNeedChangeOrApproval(project, user)
          check2 <- user.get can HideProjects in GlobalScope
        } yield {
          if (check1 || check2) Some(project) else None
        }
      } else {
        Future.successful(None)
      }
    }
  }

  private def canEditAndNeedChangeOrApproval(project: Project, user: Option[User])(implicit ec: ExecutionContext) = {
    if (project.visibility == VisibilityTypes.NeedsChanges || project.visibility == VisibilityTypes.NeedsApproval) {
      user.get can EditPages in project
    } else {
      Future.successful(false)
    }
  }

  def authedProjectActionImpl(project: Future[Option[Project]])(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = new ActionRefiner[AuthRequest, AuthedProjectRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) = project.flatMap { p =>
      implicit val r = request

      p match {
      case None => Future.successful(None)
      case Some(pr) =>
        val processed = processProject(pr, Some(request.user))
        processed.flatMap {
          case None => Future.successful(None)
          case Some(pro) => ProjectData.of(request, pro).map(Some(_))
        } map(_.map(new AuthedProjectRequest[A](_, request)))
      }
    } map { o =>
      implicit val r = request
      o.toRight(notFound())
    }

  }

  def authedProjectAction(author: String, slug: String)(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = authedProjectActionImpl(projects.withSlug(author, slug))

  def authedProjectActionById(pluginId: String)(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = authedProjectActionImpl(projects.withPluginId(pluginId))

  def organizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[OreRequest, OrganizationRequest] {
    def executionContext = ec

    def refine[A](request: OreRequest[A]) = {
      implicit val r = request
      getOrganizationData(request, organization).map { data =>
        data.map(new OrganizationRequest[A](_, request)).toRight(notFound())
      }
    }
  }

  def authedOrganizationAction(organization: String)(implicit ec: ExecutionContext) = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext = ec

    def refine[A](request: AuthRequest[A]) = {
      implicit val r = request
      getOrganizationData(request, organization).map { data =>
        data.map(new AuthedOrganizationRequest[A](_, request)).toRight(notFound())
      }
    }

  }

  def getOrganizationData(request: OreRequest[_], organization: String)(implicit ec: ExecutionContext): Future[Option[OrganizationData]] = {
    // TODO cache
    this.organizations.withName(organization).map(_.map { orga =>
      OrganizationData(request.data, orga, null, null, null) // TODO fill
    })
  }

  def getUserData(request: OreRequest[_], userName: String)(implicit ec: ExecutionContext): Future[Option[UserData]] = {
    // TODO cache
    this.users.withName(userName).map(_.map { user =>
      UserData(request.data, user, false, 0, Seq.empty, Map.empty, Map.empty)
    })
  }

}
