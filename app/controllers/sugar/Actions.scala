package controllers.sugar

import java.util.Date

import controllers.routes
import controllers.sugar.Requests._
import db.ModelService
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.project.{Project, VisibilityTypes}
import models.user.{Organization, SignOn, User}
import models.viewhelper._
import ore.permission.scope.GlobalScope
import ore.permission.{EditPages, EditSettings, HideProjects, Permission}
import play.api.cache.AsyncCacheApi
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc._
import play.api.i18n.Messages
import security.spauth.SingleSignOnConsumer
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

import util.FutureUtils
import util.functional.OptionT
import util.instances.future._
import util.syntax._

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
    this.users.current.isEmpty.map { currentUserEmpty =>
      if(noRedirect.isEmpty && currentUserEmpty)
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
  def PermissionAction[R[_] <: ScopedRequest[_]](p: Permission)(implicit ec: ExecutionContext): ActionRefiner[ScopedRequest, R] = new ActionRefiner[ScopedRequest, R] {
    def executionContext: ExecutionContext = ec

    private def log(success: Boolean, request: ScopedRequest[_]): Unit = {
      val lang = if (success) "GRANTED" else "DENIED"
      PermsLogger.debug(s"<PERMISSION $lang> ${request.user.name}@${request.path.substring(1)}")
    }

    def refine[A](request: ScopedRequest[A]): Future[Either[Result, R[A]]] = {
      implicit val r: ScopedRequest[A] = request

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
  def ProjectPermissionAction(p: Permission)(implicit ec: ExecutionContext): ActionRefiner[ScopedRequest, AuthedProjectRequest] = PermissionAction[AuthedProjectRequest](p)



  /**
    * A PermissionAction that uses an AuthedOrganizationRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return [[OrganizationRequest]]
    */
  def OrganizationPermissionAction(p: Permission)(implicit ec: ExecutionContext): ActionRefiner[ScopedRequest, AuthedOrganizationRequest] = PermissionAction[AuthedOrganizationRequest](p)

  implicit final class ResultWrapper(result: Result) {

    /**
      * Adds a new session cookie to the result for the specified [[User]].
      *
      * @param user   User to create session for
      * @param maxAge Maximum session age
      * @return Result with token
      */
    def authenticatedAs(user: User, maxAge: Int = -1)(implicit ec: ExecutionContext): Future[Result] = {
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
    def clearingSession(): Result = result.discardingCookies(DiscardingCookie(AuthTokenName))

  }

  /**
    * Returns true and marks the nonce as used if the specified nonce has not
    * been used, has not expired.
    *
    * @param nonce Nonce to check
    * @return True if valid
    */
  def isNonceValid(nonce: String)(implicit ec: ExecutionContext): Future[Boolean] = this.signOns.find(_.nonce === nonce).exists {
    signOn =>
      if (signOn.isCompleted || new Date().getTime - signOn.createdAt.value.getTime > 600000)
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
  def notFound(implicit request: OreRequest[_]): Result

  // Implementation

  def userLock(redirect: Call)(implicit ec: ExecutionContext): ActionFilter[AuthRequest] = new ActionFilter[AuthRequest] {
    def executionContext: ExecutionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
      if (!request.user.isLocked) None
      else Some(Redirect(redirect).withError("error.user.locked"))
    }
  }

  def verifiedAction(sso: Option[String], sig: Option[String])(implicit ec: ExecutionContext): ActionFilter[AuthRequest] = new ActionFilter[AuthRequest] {
    def executionContext: ExecutionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = {
      val auth = for {
        ssoSome <- sso
        sigSome <- sig
      } yield Actions.this.sso.authenticate(ssoSome, sigSome)(isNonceValid)

      OptionT.fromOption[Future](auth).flatMap(identity).cata(Some(Unauthorized), spongeUser =>
        if (spongeUser.id == request.user.id.value)
          None
        else
          Some(Unauthorized))
    }
  }

  def userAction(username: String)(implicit ec: ExecutionContext): ActionFilter[AuthRequest] = new ActionFilter[AuthRequest] {
    def executionContext: ExecutionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = {
      Actions.this.users.requestPermission(request.user, username, EditSettings).transform {
        case None => Some(Unauthorized) // No Permission
        case Some(_) => None            // Permission granted => No Filter
      }.value
    }
  }

  def oreAction(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef)
  : ActionTransformer[Request, OreRequest] = new ActionTransformer[Request, OreRequest] {
    def executionContext: ExecutionContext = ec

    def transform[A](request: Request[A]): Future[OreRequest[A]] = {
      implicit val service: ModelService = users.service
      HeaderData.of(request).map { data =>
        val requestWithLang =
          data.currentUser.flatMap(_.lang).fold(request)(lang => request.addAttr(Messages.Attrs.CurrentLang, lang))
        new OreRequest(data, requestWithLang)
      }
    }
  }

  def authAction(implicit ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[Request, AuthRequest] = new ActionRefiner[Request, AuthRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] =
      maybeAuthRequest(request, users.current(request, ec))

  }

  private def maybeAuthRequest[A](request: Request[A], futUser: OptionT[Future, User])(implicit ec: ExecutionContext,
                  asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): Future[Either[Result, AuthRequest[A]]] = {
    futUser.semiFlatMap { user =>
      implicit val service: ModelService = users.service
      HeaderData.of(request).map(hd => new AuthRequest[A](user, hd, request))
    }.toRight(onUnauthorized(request, ec)).leftSemiFlatMap(identity).value
  }

  def projectAction(author: String, slug: String)(implicit  modelService: ModelService, ec: ExecutionContext,
    asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] = maybeProjectRequest(request, Actions.this.projects.withSlug(author, slug))
  }

  def projectAction(pluginId: String)(implicit  modelService: ModelService, ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] = maybeProjectRequest(request, Actions.this.projects.withPluginId(pluginId))
  }

  private def maybeProjectRequest[A](r: OreRequest[A], project: OptionT[Future, Project])(implicit
    modelService: ModelService,
                 asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[Either[Result, ProjectRequest[A]]] = {
    implicit val request: OreRequest[A] = r
    project.flatMap { p =>
      processProject(p, request.data.currentUser)
    }.semiFlatMap { p =>
      toProjectRequest(p) { case (data, scoped) =>
        new ProjectRequest[A](data, scoped, r)
      }
    }.toRight(notFound).value
  }

  private def toProjectRequest[T](project: Project)(f: (ProjectData, ScopedProjectData) => T)(implicit
    request: OreRequest[_],
                    modelService: ModelService, ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = {
    (ProjectData.of(project), ScopedProjectData.of(request.data.currentUser, project)).parMapN(f)
  }

  private def processProject(project: Project, user: Option[User])(implicit ec: ExecutionContext) : OptionT[Future,
    Project] = {
    if (project.visibility == VisibilityTypes.Public || project.visibility == VisibilityTypes.New) {
      OptionT.pure[Future](project)
    } else {
      if (user.isDefined) {
        val check1 = canEditAndNeedChangeOrApproval(project, user)
        val check2 = user.get can HideProjects in GlobalScope

        OptionT(
          FutureUtils.raceBoolean(check1, check2).map {
            case true => Some(project)
            case false => None
          }
        )
      } else {
        OptionT.none[Future, Project]
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

  def authedProjectActionImpl(project: OptionT[Future, Project])(implicit  modelService: ModelService, ec: ExecutionContext,
            asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[AuthRequest, AuthedProjectRequest] = new ActionRefiner[AuthRequest, AuthedProjectRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedProjectRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      project.flatMap { pr =>
        processProject(pr, Some(request.user)).semiFlatMap { p =>
          toProjectRequest(p) { case (data, scoped) =>
            new AuthedProjectRequest[A](data, scoped, request)
          }
        }
      }.toRight(notFound).value
    }
  }

  def authedProjectAction(author: String, slug: String)(implicit  modelService: ModelService, ec: ExecutionContext,
          asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withSlug(author, slug))

  def authedProjectActionById(pluginId: String)(implicit  modelService: ModelService, ec: ExecutionContext,
          asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withPluginId(pluginId))



  def organizationAction(organization: String)(implicit modelService: ModelService, ec: ExecutionContext,
          asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[OreRequest, OrganizationRequest]
  = new ActionRefiner[OreRequest, OrganizationRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, OrganizationRequest[A]]] = {
      implicit val r: OreRequest[A] = request
      getOrga(request, organization).value.flatMap {
        maybeOrgaRequest(_) { case (data, scoped) =>
          new OrganizationRequest[A](data, scoped, request)
        }
      }
    }
  }

  def authedOrganizationAction(organization: String)(implicit modelService: ModelService, ec: ExecutionContext,
             asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): ActionRefiner[AuthRequest, AuthedOrganizationRequest] = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedOrganizationRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      getOrga(request, organization).value.flatMap {
        maybeOrgaRequest(_) { case (data, scoped) =>
          new AuthedOrganizationRequest[A](data, scoped, request)
        }
      }
    }

  }

  private def maybeOrgaRequest[T](maybeOrga: Option[Organization])(f: (OrganizationData, ScopedOrganizationData) =>
    T)(implicit request: OreRequest[_],
                     modelService: ModelService, ec: ExecutionContext, asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef) = {
    maybeOrga match {
      case None => Future.successful(Left(notFound))
      case Some(orga) =>
        val rf = Function.untupled(f.tupled.andThen(Right.apply))
        (OrganizationData.of(orga), ScopedOrganizationData.of(request.data.currentUser, orga)).parMapN(rf)
    }
  }

  def getOrga(request: OreRequest[_], organization: String)(implicit modelService: ModelService, ec: ExecutionContext,
          asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): OptionT[Future, Organization] = {
    this.organizations.withName(organization)
  }

  def getUserData(request: OreRequest[_], userName: String)(implicit modelService: ModelService, ec: ExecutionContext,
          asyncCacheApi: AsyncCacheApi, db: JdbcBackend#DatabaseDef): OptionT[Future, UserData] = {
    this.users.withName(userName).semiFlatMap(user => UserData.of(request, user))
  }

}
