package controllers

import controllers.sugar.Requests.{AuthRequest, AuthedProjectRequest, OreRequest}
import controllers.sugar.{Actions, Bakery, Requests}
import db.ModelService
import db.access.ModelAccess
import db.impl.VersionTable
import db.impl.OrePostgresDriver.api._
import models.project.{Project, Version, VisibilityTypes}
import models.user.SignOn
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc._
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.StringUtils._
import util.instances.future._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

import controllers.OreBaseController.{BindFormEitherTPartiallyApplied, BindFormOptionTPartiallyApplied}
import play.api.data.Form
import slick.jdbc.JdbcBackend
import util.functional.{EitherT, Monad, OptionT}
import ore.permission.ReviewProjects

/**
  * Represents a Secured base Controller for this application.
  */
abstract class OreBaseController(implicit val env: OreEnv,
                                 val config: OreConfig,
                                 val service: ModelService,
                                 val bakery: Bakery,
                                 val auth: SpongeAuthApi,
                                 val sso: SingleSignOnConsumer,
                                 val cache: AsyncCacheApi)
                              extends InjectedController
                                with Actions
                                with I18nSupport {

  implicit val db: JdbcBackend#DatabaseDef = service.DB.db

  override val signOns: ModelAccess[SignOn] = this.service.access[SignOn](classOf[SignOn])

  override def notFound(implicit request: OreRequest[_]) = NotFound(views.html.errors.notFound())

  implicit def ec: ExecutionContext

  /**
    * Gets a project with the specified author and slug, or returns a notFound.
    *
    * @param author   Project author
    * @param slug     Project slug
    * @param request  Incoming request
    * @return         NotFound or project
    */
  def getProject(author: String, slug: String)(implicit request: OreRequest[_]): EitherT[Future, Result, Project]
  = projects.withSlug(author, slug).toRight(notFound)

  private def versionFindFunc(versionString: String, canSeeHiden: Boolean): VersionTable => Rep[Boolean] = v => {
    val versionMatches = v.versionString.toLowerCase === versionString.toLowerCase
    val isVisible = if(canSeeHiden) true.bind else v.visibility === VisibilityTypes.Public
    versionMatches && isVisible
  }

  /**
    * Gets a project with the specified versionString, or returns a notFound.
    *
    * @param project        Project to get version from
    * @param versionString  VersionString
    * @param request        Incoming request
    * @return               NotFound or function result
    */
  def getVersion(project: Project, versionString: String)
                 (implicit request: OreRequest[_]): EitherT[Future, Result, Version]
  = project.versions.find(versionFindFunc(versionString, request.data.globalPerm(ReviewProjects))).toRight(notFound)

  /**
    * Gets a version with the specified author, project slug and version string
    * or returns a notFound.
    *
    * @param author         Project author
    * @param slug           Project slug
    * @param versionString  VersionString
    * @param request        Incoming request
    * @return               NotFound or project
    */
  def getProjectVersion(author: String, slug: String, versionString: String)(implicit request: OreRequest[_]): EitherT[Future, Result, Version]
  = for {
    project <- getProject(author, slug)
    version <- getVersion(project, versionString)
  } yield version

  def bindFormEitherT[F[_]] = new BindFormEitherTPartiallyApplied[F]

  def bindFormOptionT[F[_]] = new BindFormOptionTPartiallyApplied[F]

  def OreAction: ActionBuilder[OreRequest, AnyContent] = Action andThen oreAction

  /** Ensures a request is authenticated */
  def Authenticated: ActionBuilder[AuthRequest, AnyContent] = Action andThen authAction

  /** Ensures a user's account is unlocked */
  def UserLock(redirect: Call = ShowHome): ActionBuilder[AuthRequest, AnyContent] = Authenticated andThen userLock(redirect)

  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Request with a project if found, NotFound otherwise.
    */
  def ProjectAction(author: String, slug: String): ActionBuilder[Requests.ProjectRequest, AnyContent] = OreAction andThen projectAction(author, slug)


  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param pluginId The project's unique plugin ID
    * @return         Request with a project if found, NotFound otherwise
    */
  def ProjectAction(pluginId: String): ActionBuilder[Requests.ProjectRequest, AnyContent] = OreAction andThen projectAction(pluginId)

  /**
    * Ensures a request is authenticated and retrieves, processes, and adds a
    * [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug Project slug
    * @return Authenticated request with a project if found, NotFound otherwise.
    */
  def AuthedProjectAction(author: String, slug: String, requireUnlock: Boolean = false): ActionBuilder[AuthedProjectRequest, AnyContent] = {
    val first = if (requireUnlock) UserLock(ShowProject(author, slug)) else Authenticated
    first andThen authedProjectAction(author, slug)
  }

  def AuthedProjectActionById(pluginId: String, requireUnlock: Boolean = true): ActionBuilder[AuthedProjectRequest, AnyContent] = {
    val first = if (requireUnlock) UserLock(ShowProject(pluginId)) else Authenticated
    first andThen authedProjectActionById(pluginId)
  }

  /**
    * Retrieves an [[models.user.Organization]] and adds it to the request.
    *
    * @param organization Organization to retrieve
    * @return             Request with organization if found, NotFound otherwise
    */
  def OrganizationAction(organization: String): ActionBuilder[Requests.OrganizationRequest, AnyContent] = OreAction andThen organizationAction(organization)

  /**
    * Ensures a request is authenticated and retrieves and adds a
    * [[models.user.Organization]] to the request.
    *
    * @param organization Organization to retrieve
    * @return             Authenticated request with Organization if found, NotFound otherwise
    */
  def AuthedOrganizationAction(organization: String, requireUnlock: Boolean = false): ActionBuilder[Requests.AuthedOrganizationRequest, AnyContent] = {
    val first = if (requireUnlock) UserLock(ShowUser(organization)) else Authenticated
    first andThen authedOrganizationAction(organization)
  }

  /**
    * A request that ensures that a user has permission to edit a specified
    * profile.
    *
    * @param username User to check
    * @return [[OreAction]] if has permission
    */
  def UserAction(username: String): ActionBuilder[AuthRequest, AnyContent] = Authenticated andThen userAction(username)

  /**
    * Represents an action that requires a user to reenter their password.
    *
    * @param username Username to verify
    * @param sso      Incoming SSO payload
    * @param sig      Incoming SSO signature
    * @return         None if verified, Unauthorized otherwise
    */
  def VerifiedAction(username: String, sso: Option[String], sig: Option[String]): ActionBuilder[AuthRequest, AnyContent]
  = UserAction(username) andThen verifiedAction(sso, sig)

}
object OreBaseController {

  final class BindFormEitherTPartiallyApplied[F[_]](val b: Boolean = true) extends AnyVal {
    def apply[A, B](form: Form[B])(left: Form[B] => A)(implicit F: Monad[F], request: Request[_]): EitherT[F, A, B] =
      form.bindFromRequest().fold(left.andThen(EitherT.leftT[F, B](_)), EitherT.rightT[F, A](_))
  }

  final class BindFormOptionTPartiallyApplied[F[_]](val b: Boolean = true) extends AnyVal {
    def apply[A](form: Form[A])(implicit F: Monad[F], request: Request[_]): OptionT[F, A] =
      form.bindFromRequest().fold(_ => OptionT.none[F, A], OptionT.some[F](_))
  }
}
