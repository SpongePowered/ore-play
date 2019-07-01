package controllers.sugar

import scala.language.higherKinds

import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.{Lang, Messages}
import play.api.mvc.Results.{Redirect, Unauthorized}
import play.api.mvc._

import controllers.OreControllerComponents
import controllers.sugar.Requests._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.viewhelper._
import ore.OreConfig
import ore.auth.SSOApi
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{Model, ModelService}
import ore.models.organization.Organization
import ore.models.project.io.ProjectFiles
import ore.models.project.{Project, Visibility}
import ore.models.user.{SignOn, User}
import ore.permission.Permission
import ore.permission.scope.{GlobalScope, HasScope}
import ore.util.OreMDC
import util.syntax._

import cats.Parallel
import cats.syntax.all._
import com.typesafe.scalalogging
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

/**
  * A set of actions used by Ore.
  */
trait Actions extends Calls with ActionHelpers { self =>

  def oreComponents: OreControllerComponents

  def bakery: Bakery             = oreComponents.bakery
  implicit def config: OreConfig = oreComponents.config

  type ParTask[+A]    = zio.interop.ParIO[Any, Throwable, A]
  type ParUIO[+A]     = zio.interop.ParIO[Any, Nothing, A]
  type RIO[-R, +A]    = ZIO[R, Nothing, A]
  type ParRIO[-R, +A] = zio.interop.ParIO[R, Nothing, A]

  implicit val parUIO: Parallel[UIO, ParUIO]                                  = parallelInstance[Any, Nothing]
  implicit val parTask: Parallel[Task, ParTask]                               = parallelInstance[Any, Throwable]
  implicit val parBlockingIO: Parallel[RIO[Blocking, ?], ParRIO[Blocking, ?]] = parallelInstance[Blocking, Nothing]

  implicit def service: ModelService[UIO]           = oreComponents.uioEffects.service
  def sso: SSOApi[UIO]                              = oreComponents.uioEffects.sso
  implicit def users: UserBase[UIO]                 = oreComponents.uioEffects.users
  implicit def projects: ProjectBase[UIO]           = oreComponents.uioEffects.projects
  implicit def organizations: OrganizationBase[UIO] = oreComponents.uioEffects.organizations

  implicit def projectFiles: ProjectFiles[RIO[Blocking, ?]] = oreComponents.projectFiles

  implicit def ec: ExecutionContext = oreComponents.executionContext

  private val PermsLogger    = scalalogging.Logger("Permissions")
  private val MDCPermsLogger = scalalogging.Logger.takingImplicit[OreMDC](PermsLogger.underlying)

  val AuthTokenName = "_oretoken"

  implicit def zioRuntime: zio.Runtime[Blocking with Clock] = oreComponents.zioRuntime

  protected def zioToFuture[A](zio: RIO[Blocking, A]): Future[A] =
    ActionHelpers.zioToFuture(zio)

  /** Called when a [[User]] tries to make a request they do not have permission for */
  def onUnauthorized(implicit request: Request[_]): IO[Result, Nothing] = {
    val noRedirect           = request.flash.get("noRedirect")
    implicit val mdc: OreMDC = OreMDC.NoMDC
    users.current.isEmpty
      .map { currentUserEmpty =>
        if (noRedirect.isEmpty && currentUserEmpty)
          Redirect(controllers.routes.Users.logIn(None, None, Some(request.path)))
        else
          Redirect(ShowHome)
      }
      .flatMap(res => ZIO.fail(res))
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
  )(implicit ec: ExecutionContext, hasScope: HasScope[R[_]]): ActionRefiner[R, R] =
    new ActionRefiner[R, R] {
      def executionContext: ExecutionContext = ec

      private def log(success: Boolean, request: R[_]): Unit = {
        val lang = if (success) "GRANTED" else "DENIED"
        MDCPermsLogger.debug(s"<PERMISSION $lang> ${request.user.name}@${request.path.substring(1)}")(
          request: OreRequest[_]
        )
      }

      def refine[A](request: R[A]): Future[Either[Result, R[A]]] = {
        implicit val r: R[A] = request

        zioToFuture(
          for {
            perms <- request.user.permissionsIn(request).orDie
            hasPerm = perms.has(p)
            _       = log(success = hasPerm, request)
            res <- (
              if (!hasPerm) onUnauthorized
              else UIO.succeed(request)
            ).either
          } yield res
        )
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
    def authenticatedAs(user: User, maxAge: Int = -1): UIO[Result] = {
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
  def isNonceValid(nonce: String): UIO[Boolean] =
    ModelView
      .now(SignOn)
      .find(_.nonce === nonce)
      .semiflatMap { signOn =>
        if (signOn.isCompleted || Instant.now().toEpochMilli - signOn.createdAt.toEpochMilli > 600000)
          UIO.succeed(false)
        else {
          service.update(signOn)(_.copy(isCompleted = true)).const(true)
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
        ssoSome <- ZIO.fromOption(sso)
        sigSome <- ZIO.fromOption(sig)
        res     <- self.sso.authenticate(ssoSome, sigSome)(isNonceValid).get
      } yield res

      zioToFuture(
        auth.fold(
          _ => Some(Unauthorized),
          spongeUser => if (spongeUser.id == request.user.id.value) None else Some(Unauthorized)
        )
      )
    }
  }

  def userEditAction(username: String)(implicit ec: ExecutionContext): ActionFilter[AuthRequest] =
    new ActionFilter[AuthRequest] {
      def executionContext: ExecutionContext = ec

      def filter[A](request: AuthRequest[A]): Future[Option[Result]] = {
        zioToFuture(
          users
            .requestPermission(request.user, username, Permission.EditOwnUserSettings)(request)
            .transform {
              case None    => Some(Unauthorized) // No Permission
              case Some(_) => None               // Permission granted => No Filter
            }
            .value
        )
      }
    }

  def oreAction(
      implicit ec: ExecutionContext
  ): ActionTransformer[Request, OreRequest] = new ActionTransformer[Request, OreRequest] {
    def executionContext: ExecutionContext = ec

    def transform[A](request: Request[A]): Future[OreRequest[A]] = {
      zioToFuture(
        HeaderData
          .of(request)
          .map { data =>
            val requestWithLang =
              data.currentUser
                .flatMap(_.lang.map(Lang.apply))
                .fold(request)(lang => request.addAttr(Messages.Attrs.CurrentLang, lang))
            new SimpleOreRequest(data, requestWithLang)
          }
      )
    }
  }

  def authAction(
      implicit ec: ExecutionContext
  ): ActionRefiner[Request, AuthRequest] = new ActionRefiner[Request, AuthRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] =
      maybeAuthRequest(request, users.current(request, OreMDC.NoMDC).toZIO)

  }

  private def maybeAuthRequest[A](
      request: Request[A],
      userF: IO[Unit, Model[User]]
  ): Future[Either[Result, AuthRequest[A]]] = {
    val authRequest = for {
      user       <- userF
      headerData <- HeaderData.of(request)
    } yield new AuthRequest(user, headerData, request)

    val withError = authRequest.flatMapError(_ => onUnauthorized(request).flip)

    zioToFuture(withError.either)
  }

  def projectAction(author: String, slug: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] =
      maybeProjectRequest(request, projects.withSlug(author, slug).get)
  }

  def projectAction(pluginId: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[OreRequest, ProjectRequest] = new ActionRefiner[OreRequest, ProjectRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, ProjectRequest[A]]] =
      maybeProjectRequest(request, projects.withPluginId(pluginId).get)
  }

  private def maybeProjectRequest[A](
      r: OreRequest[A],
      projectF: IO[Unit, Model[Project]]
  ): Future[Either[Result, ProjectRequest[A]]] = {
    implicit val request: OreRequest[A] = r

    val projectRequest = for {
      project    <- projectF
      newProject <- processProject(project, request.headerData.currentUser)
      res <- toProjectRequest(newProject) {
        case (data, scoped) => new ProjectRequest[A](data, scoped, r.headerData, r)
      }
    } yield res

    zioToFuture(projectRequest.constError(notFound).either)
  }

  private def toProjectRequest[T](project: Model[Project])(f: (ProjectData, ScopedProjectData) => T)(
      implicit
      request: OreRequest[_]
  ) = {
    val projectData = ProjectData.of[ZIO[Blocking, Throwable, ?], zio.interop.ParIO[Blocking, Throwable, ?]](project)
    (projectData.orDie, ScopedProjectData.of[UIO, ParUIO](request.headerData.currentUser, project)).parMapN(f)
  }

  private def processProject(project: Model[Project], optUser: Option[Model[User]]): IO[Unit, Model[Project]] = {
    if (project.visibility == Visibility.Public) {
      IO.succeed(project)
    } else {
      for {
        user <- ZIO.fromOption(optUser)

        check1 = canEditAndNeedChangeOrApproval(project, user)
        check2 = user.permissionsIn(GlobalScope).map(_.has(Permission.SeeHidden))

        canSeeProject <- check1.race(check2)
        res           <- if (canSeeProject) IO.succeed(project) else IO.fail(())
      } yield res
    }
  }

  private def canEditAndNeedChangeOrApproval(project: Model[Project], user: Model[User]) =
    project.visibility match {
      case Visibility.New => user.permissionsIn(project).map(_.has(Permission.CreateVersion))
      case Visibility.NeedsApproval | Visibility.NeedsApproval =>
        user.permissionsIn(project).map(_.has(Permission.EditProjectSettings))
      case _ => UIO.succeed(false)
    }

  def authedProjectActionImpl(projectF: IO[Unit, Model[Project]])(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = new ActionRefiner[AuthRequest, AuthedProjectRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedProjectRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      val projectRequest = for {
        project    <- projectF
        newProject <- processProject(project, Some(request.user))
        projectRequest <- toProjectRequest(newProject) {
          case (data, scoped) => new AuthedProjectRequest[A](data, scoped, r.headerData, request)
        }
      } yield projectRequest

      zioToFuture(projectRequest.constError(notFound).either)
    }
  }

  def authedProjectAction(author: String, slug: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withSlug(author, slug).get)

  def authedProjectActionById(pluginId: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthRequest, AuthedProjectRequest] = authedProjectActionImpl(projects.withPluginId(pluginId).get)

  def organizationAction(organization: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[OreRequest, OrganizationRequest] = new ActionRefiner[OreRequest, OrganizationRequest] {

    def executionContext: ExecutionContext = ec

    def refine[A](request: OreRequest[A]): Future[Either[Result, OrganizationRequest[A]]] = {
      implicit val r: OreRequest[A] = request

      val orgaRequest = for {
        org <- getOrga(organization)
        orgaRequest <- toOrgaRequest(org) {
          case (data, scoped) => new OrganizationRequest[A](data, scoped, r.headerData, request)
        }
      } yield orgaRequest

      zioToFuture(orgaRequest.constError(notFound).either)
    }
  }

  def authedOrganizationAction(organization: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[AuthRequest, AuthedOrganizationRequest] = new ActionRefiner[AuthRequest, AuthedOrganizationRequest] {
    def executionContext: ExecutionContext = ec

    def refine[A](request: AuthRequest[A]): Future[Either[Result, AuthedOrganizationRequest[A]]] = {
      implicit val r: AuthRequest[A] = request

      val orgaRequest = for {
        org <- getOrga(organization)
        orgaRequest <- toOrgaRequest(org) {
          case (data, scoped) => new AuthedOrganizationRequest[A](data, scoped, r.headerData, request)
        }
      } yield orgaRequest

      zioToFuture(orgaRequest.constError(notFound).either)
    }
  }

  private def toOrgaRequest[T](orga: Model[Organization])(f: (OrganizationData, ScopedOrganizationData) => T)(
      implicit request: OreRequest[_]
  ) = {
    val orgData = OrganizationData.of[Task, ParTask](orga)
    (orgData.orDie, ScopedOrganizationData.of(request.headerData.currentUser, orga)).parMapN(f)
  }

  def getOrga(organization: String): IO[Unit, Model[Organization]] =
    organizations.withName(organization).get

  def getUserData(request: OreRequest[_], userName: String): IO[Unit, UserData] =
    users.withName(userName)(request).semiflatMap(UserData.of(request, _)).toZIO

}
