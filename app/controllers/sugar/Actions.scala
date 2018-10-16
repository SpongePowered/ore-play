package controllers.sugar

import scala.language.higherKinds

import java.util.Date

import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.Messages
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc._

import controllers.routes
import controllers.sugar.Requests._
import db.ModelService
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.project.{Project, Visibility}
import models.user.{Organization, SignOn, User}
import models.viewhelper._
import ore.permission.scope.{GlobalScope, HasScope}
import ore.permission.{EditPages, EditSettings, HideProjects, Permission}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.FutureUtils

import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._
import org.slf4j.MDC
import slick.jdbc.JdbcBackend

/**
  * A set of actions used by Ore.
  */
trait Actions extends Calls with ActionHelpers {

  implicit def service: ModelService
  def sso: SingleSignOnConsumer
  def signOns: ModelAccess[SignOn]
  def bakery: Bakery
  implicit def auth: SpongeAuthApi

  def users: UserBase                 = UserBase()
  def projects: ProjectBase           = ProjectBase()
  def organizations: OrganizationBase = OrganizationBase()

  val PermsLogger = play.api.Logger("Permissions")

  val AuthTokenName = "_oretoken"

  /** Called when a [[User]] tries to make a request they do not have permission for */
  def onUnauthorized(implicit request: Request[_], ec: ExecutionContext): Future[Result] = {
    val noRedirect = request.flash.get("noRedirect")
    users.current.isEmpty.map { currentUserEmpty =>
      if (noRedirect.isEmpty && currentUserEmpty)
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
  def PermissionAction[R[_] <: ScopedRequest[_]](
      p: Permission
  )(implicit ec: ExecutionContext, hasScope: HasScope[R[_]]): ActionRefiner[R, R] = new ActionRefiner[R, R] {
    def executionContext: ExecutionContext = ec

    private def log(success: Boolean, request: R[_]): Unit = {
      val lang = if (success) "GRANTED" else "DENIED"
      PermsLogger.debug(s"<PERMISSION $lang> ${request.user.name}@${request.path.substring(1)}")
    }

    def refine[A](request: R[A]): Future[Either[Result, R[A]]] = {
      implicit val r: R[A] = request

      request.user.can(p).in(request).flatMap { perm =>
        log(success = perm, request)
        if (!perm) onUnauthorized.map(Left.apply)
        else Future.successful(Right(request))
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
  def ProjectPermissionAction(p: Permission)(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthedProjectRequest, AuthedProjectRequest] = PermissionAction[AuthedProjectRequest](p)

  /**
    * A PermissionAction that uses an AuthedOrganizationRequest for the
    * ScopedRequest.
    *
    * @param p Permission to check
    * @return [[OrganizationRequest]]
    */
  def OrganizationPermissionAction(p: Permission)(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthedOrganizationRequest, AuthedOrganizationRequest] =
    PermissionAction[AuthedOrganizationRequest](p)

  implicit final class ResultWrapper(result: Result) {

    /**
      * Adds a new session cookie to the result for the specified [[User]].
      *
      * @param user   User to create session for
      * @param maxAge Maximum session age
      * @return Result with token
      */
    def authenticatedAs(user: User, maxAge: Int = -1)(implicit ec: ExecutionContext): Future[Result] = {
      val session = users.createSession(user)
      val age     = if (maxAge == -1) None else Some(maxAge)
      session.map { s =>
        result.withCookies(bakery.bake(AuthTokenName, s.token, age))
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
  def isNonceValid(nonce: String)(implicit ec: ExecutionContext): Future[Boolean] =
    this.signOns
      .find(_.nonce === nonce)
      .semiflatMap { signOn =>
        if (signOn.isCompleted || new Date().getTime - signOn.createdAt.value.getTime > 600000)
          Future.successful(false)
        else {
          service.update(signOn.copy(isCompleted = true)).as(true)
        }
      }
      .exists(identity)

  /**
    * Returns a NotFound result with the 404 HTML template.
    *
    * @return NotFound
    */
  def notFound(implicit request: OreRequest[_]): Result

  // Implementation

  def userLock(redirect: Call)(implicit ec: ExecutionContext): ActionFilter[AuthRequest] =
    new ActionFilter[AuthRequest] {
      def executionContext: ExecutionContext = ec

      def filter[A](request: AuthRequest[A]): Future[Option[Result]] = Future.successful {
        if (!request.user.isLocked) None
        else Some(Redirect(redirect).withError("error.user.locked"))
      }
    }

  def verifiedAction(sso: Option[String], sig: Option[String])(
      implicit ec: ExecutionContext
  ): ActionFilter[AuthRequest] = new ActionFilter[AuthRequest] {
    def executionContext: ExecutionContext = ec

    def filter[A](request: AuthRequest[A]): Future[Option[Result]] = {
      val auth = for {
        ssoSome <- OptionT.fromOption[Future](sso)
        sigSome <- OptionT.fromOption[Future](sig)
        res     <- Actions.this.sso.authenticate(ssoSome, sigSome)(isNonceValid)
      } yield res

      auth.cata(
        Some(Unauthorized),
        spongeUser => if (spongeUser.id == request.user.id.value) None else Some(Unauthorized)
      )
    }
  }

  def userAction(username: String)(implicit ec: ExecutionContext): ActionFilter[AuthRequest] =
    new ActionFilter[AuthRequest] {
      def executionContext: ExecutionContext = ec

      def filter[A](request: AuthRequest[A]): Future[Option[Result]] =
        users
          .requestPermission(request.user, username, EditSettings)
          .transform {
            case None    => Some(Unauthorized) // No Permission
            case Some(_) => None // Permission granted => No Filter
          }
          .value
    }

  def oreAction(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionTransformer[Request, OreRequest] = new ActionTransformer[Request, OreRequest] {
    def executionContext: ExecutionContext = ec

    def transform[A](request: Request[A]): Future[OreRequest[A]] = {
      HeaderData.of(request).map { data =>
        val requestWithLang =
          data.currentUser.flatMap(_.lang).fold(request)(lang => request.addAttr(Messages.Attrs.CurrentLang, lang))
        new SimpleOreRequest(data, requestWithLang)
      }
    }
  }

  def authAction(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[Request, AuthRequest] = new ActionRefiner[Request, AuthRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] =
      maybeAuthRequest(request, users.current(request, ec, auth))

  }

  private def maybeAuthRequest[A](request: Request[A], futUser: OptionT[Future, User])(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): Future[Either[Result, AuthRequest[A]]] =
    futUser
      .semiflatMap(user => HeaderData.of(request).map(new AuthRequest(user, _, request)))
      .toRight(onUnauthorized(request, ec))
      .leftSemiflatMap(identity)
      .value

  def projectAction(author: String, slug: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] =
      maybeProjectRequest(request, projects.withSlug(author, slug))
  }

  def projectAction(pluginId: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] =
      maybeProjectRequest(request, projects.withPluginId(pluginId))
  }

  private def maybeProjectRequest[A](r: OreRequest[A], project: OptionT[Future, Project])(
      implicit
      db: JdbcBackend#DatabaseDef,
      ec: ExecutionContext
  ): Future[Either[Result, ProjectRequest[A]]] = {
    implicit val request: OreRequest[A] = r
    project
      .flatMap(processProject(_, request.headerData.currentUser))
      .semiflatMap { p =>
        toProjectRequest(p) {
          case (data, scoped) =>
            MDC.put("currentProjectId", data.project.id.toString)
            MDC.put("currentProjectSlug", data.project.slug)

            new ProjectRequest[A](data, scoped, r.headerData, r)
        }
      }
      .toRight(notFound)
      .value
  }

  private def toProjectRequest[T](project: Project)(f: (ProjectData, ScopedProjectData) => T)(
      implicit
      request: OreRequest[_],
      ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ) =
    (ProjectData.of(project), ScopedProjectData.of(request.headerData.currentUser, project)).mapN(f)

  private def processProject(project: Project, user: Option[User])(
      implicit ec: ExecutionContext
  ): OptionT[Future, Project] = {
    if (project.visibility == Visibility.Public || project.visibility == Visibility.New) {
      OptionT.pure[Future](project)
    } else {
      OptionT
        .fromOption[Future](user)
        .semiflatMap { user =>
          val check1 = canEditAndNeedChangeOrApproval(project, user)
          val check2 = user.can(HideProjects).in(GlobalScope)

          FutureUtils.raceBoolean(check1, check2)
        }
        .subflatMap {
          case true  => Some(project)
          case false => None
        }
    }
  }

  private def canEditAndNeedChangeOrApproval(project: Project, user: User)(implicit ec: ExecutionContext) = {
    if (project.visibility == Visibility.NeedsChanges || project.visibility == Visibility.NeedsApproval) {
      user.can(EditPages).in(project)
    } else {
      Future.successful(false)
    }
  }

  def authedProjectActionImpl(project: OptionT[Future, Project])(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = new ActionRefiner[AuthRequest, AuthedProjectRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedProjectRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      project
        .flatMap(processProject(_, Some(request.user)))
        .semiflatMap { p =>
          toProjectRequest(p) {
            case (data, scoped) => new AuthedProjectRequest[A](data, scoped, r.headerData, request)
          }
        }
        .toRight(notFound)
        .value
    }
  }

  def authedProjectAction(author: String, slug: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withSlug(author, slug))

  def authedProjectActionById(pluginId: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withPluginId(pluginId))

  def organizationAction(organization: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[OreRequest, OrganizationRequest] = new ActionRefiner[OreRequest, OrganizationRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, OrganizationRequest[A]]] = {
      implicit val r: OreRequest[A] = request
      getOrga(organization)
        .semiflatMap { org =>
          toOrgaRequest(org) {
            case (data, scoped) => new OrganizationRequest[A](data, scoped, r.headerData, request)
          }
        }
        .toRight(notFound)
        .value
    }
  }

  def authedOrganizationAction(organization: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): ActionRefiner[AuthRequest, AuthedOrganizationRequest] = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedOrganizationRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      getOrga(organization)
        .semiflatMap { org =>
          toOrgaRequest(org) {
            case (data, scoped) => new AuthedOrganizationRequest[A](data, scoped, r.headerData, request)
          }
        }
        .toRight(notFound)
        .value
    }

  }

  private def toOrgaRequest[T](orga: Organization)(f: (OrganizationData, ScopedOrganizationData) => T)(
      implicit request: OreRequest[_],
      ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ) = {
    MDC.put("currentOrgaId", orga.id.toString)
    MDC.put("currentOrgaName", orga.name)

    (OrganizationData.of(orga), ScopedOrganizationData.of(request.headerData.currentUser, orga)).mapN(f)
  }

  def getOrga(organization: String)(
      implicit ec: ExecutionContext,
  ): OptionT[Future, Organization] =
    organizations.withName(organization)

  def getUserData(request: OreRequest[_], userName: String)(
      implicit ec: ExecutionContext,
      db: JdbcBackend#DatabaseDef
  ): OptionT[Future, UserData] =
    users.withName(userName).semiflatMap(UserData.of(request, _))

}
