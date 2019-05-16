package controllers

import scala.language.higherKinds

import scala.concurrent.ExecutionContext

import play.api.i18n.I18nSupport
import play.api.mvc._

import controllers.sugar.Requests.{AuthRequest, AuthedProjectRequest, OreRequest}
import controllers.sugar.{Actions, Bakery, Requests}
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.VersionTable
import ore.models.project.{Project, Version, Visibility}
import ore.db.access.ModelView
import ore.db.{Model, ModelService}
import ore.models.organization.Organization
import ore.permission.Permission
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}

import cats.data.EitherT
import cats.effect.{ContextShift, IO}

/**
  * Represents a Secured base Controller for this application.
  */
abstract class OreBaseController(
    implicit val env: OreEnv,
    val config: OreConfig,
    val service: ModelService[IO],
    val bakery: Bakery,
    val auth: SpongeAuthApi,
    val sso: SingleSignOnConsumer
) extends InjectedController
    with Actions
    with I18nSupport {

  override def notFound(implicit request: OreRequest[_]): Result = NotFound(views.html.errors.notFound())

  implicit def ec: ExecutionContext
  implicit def cs: ContextShift[IO] = IO.contextShift(ec)

  /**
    * Gets a project with the specified author and slug, or returns a notFound.
    *
    * @param author   Project author
    * @param slug     Project slug
    * @param request  Incoming request
    * @return         NotFound or project
    */
  def getProject(author: String, slug: String)(implicit request: OreRequest[_]): EitherT[IO, Result, Model[Project]] =
    projects.withSlug(author, slug).toRight(notFound)

  private def versionFindFunc(versionString: String, canSeeHiden: Boolean): VersionTable => Rep[Boolean] = v => {
    val versionMatches = v.versionString.toLowerCase === versionString.toLowerCase
    val isVisible      = if (canSeeHiden) true.bind else v.visibility === (Visibility.Public: Visibility)
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
  def getVersion(project: Model[Project], versionString: String)(
      implicit request: OreRequest[_]
  ): EitherT[IO, Result, Model[Version]] =
    project
      .versions(ModelView.now(Version))
      .find(versionFindFunc(versionString, request.headerData.globalPerm(Permission.SeeHidden)))
      .toRight(notFound)

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
  def getProjectVersion(author: String, slug: String, versionString: String)(
      implicit request: OreRequest[_]
  ): EitherT[IO, Result, Model[Version]] =
    for {
      project <- getProject(author, slug)
      version <- getVersion(project, versionString)
    } yield version

  def OreAction: ActionBuilder[OreRequest, AnyContent] = Action.andThen(oreAction)

  /** Ensures a request is authenticated */
  def Authenticated: ActionBuilder[AuthRequest, AnyContent] = Action.andThen(authAction)

  /** Ensures a user's account is unlocked */
  def UserLock(redirect: Call = ShowHome): ActionBuilder[AuthRequest, AnyContent] =
    Authenticated.andThen(userLock(redirect))

  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Request with a project if found, NotFound otherwise.
    */
  def ProjectAction(author: String, slug: String): ActionBuilder[Requests.ProjectRequest, AnyContent] =
    OreAction.andThen(projectAction(author, slug))

  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param pluginId The project's unique plugin ID
    * @return         Request with a project if found, NotFound otherwise
    */
  def ProjectAction(pluginId: String): ActionBuilder[Requests.ProjectRequest, AnyContent] =
    OreAction.andThen(projectAction(pluginId))

  /**
    * Ensures a request is authenticated and retrieves, processes, and adds a
    * [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug Project slug
    * @return Authenticated request with a project if found, NotFound otherwise.
    */
  def AuthedProjectAction(
      author: String,
      slug: String,
      requireUnlock: Boolean = false
  ): ActionBuilder[AuthedProjectRequest, AnyContent] = {
    val first = if (requireUnlock) UserLock(ShowProject(author, slug)) else Authenticated
    first.andThen(authedProjectAction(author, slug))
  }

  /**
    * Retrieves an [[Organization]] and adds it to the request.
    *
    * @param organization Organization to retrieve
    * @return             Request with organization if found, NotFound otherwise
    */
  def OrganizationAction(organization: String): ActionBuilder[Requests.OrganizationRequest, AnyContent] =
    OreAction.andThen(organizationAction(organization))

  /**
    * Ensures a request is authenticated and retrieves and adds a
    * [[Organization]] to the request.
    *
    * @param organization Organization to retrieve
    * @return             Authenticated request with Organization if found, NotFound otherwise
    */
  def AuthedOrganizationAction(
      organization: String,
      requireUnlock: Boolean = false
  ): ActionBuilder[Requests.AuthedOrganizationRequest, AnyContent] = {
    val first = if (requireUnlock) UserLock(ShowUser(organization)) else Authenticated
    first.andThen(authedOrganizationAction(organization))
  }

  /**
    * A request that ensures that a user has permission to edit a specified
    * profile.
    *
    * @param username User to check
    * @return [[OreAction]] if has permission
    */
  def UserEditAction(username: String): ActionBuilder[AuthRequest, AnyContent] =
    Authenticated.andThen(userEditAction(username))

  /**
    * Represents an action that requires a user to reenter their password.
    *
    * @param username Username to verify
    * @param sso      Incoming SSO payload
    * @param sig      Incoming SSO signature
    * @return         None if verified, Unauthorized otherwise
    */
  def VerifiedAction(
      username: String,
      sso: Option[String],
      sig: Option[String]
  ): ActionBuilder[AuthRequest, AnyContent] = UserEditAction(username).andThen(verifiedAction(sso, sig))
}
