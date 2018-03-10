package controllers

import controllers.sugar.Requests.AuthRequest
import controllers.sugar.{Actions, Bakery}
import db.ModelService
import db.access.ModelAccess
import db.impl.VersionTable
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import models.project.{Project, Version}
import models.user.SignOn
import ore.{OreConfig, OreEnv}
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc._
import security.spauth.SingleSignOnConsumer
import util.StringUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Represents a Secured base Controller for this application.
  */
abstract class OreBaseController(implicit val env: OreEnv,
                                 val config: OreConfig,
                                 val service: ModelService,
                                 override val bakery: Bakery,
                                 override val sso: SingleSignOnConsumer)
                              extends InjectedController
                                with Actions
                                with I18nSupport {

  implicit override val users: UserBase = this.service.getModelBase(classOf[UserBase])
  implicit override val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])
  implicit override val organizations: OrganizationBase = this.service.getModelBase(classOf[OrganizationBase])
  implicit val lang = Lang.defaultLang

  override val signOns: ModelAccess[SignOn] = this.service.access[SignOn](classOf[SignOn])

  override def notFound()(implicit request: Request[_]) = NotFound(views.html.errors.notFound())

  /**
    * Executes the given function with the specified result or returns a
    * NotFound if not found.
    *
    * @param author   Project author
    * @param slug     Project slug
    * @param fn       Function to execute
    * @param request  Incoming request
    * @return         NotFound or function result
    */
  def withProject(author: String, slug: String)(fn: Project => Result)(implicit request: Request[_]): Future[Result]
  = this.projects.withSlug(author, slug).map(_.map(fn).getOrElse(notFound))

  def withProjectAsync(author: String, slug: String)(fn: Project => Future[Result])(implicit request: Request[_]): Future[Result]
  = this.projects.withSlug(author, slug).flatMap(_.map(fn).getOrElse(Future(notFound)))

  /**
    * Executes the given function with the specified result or returns a
    * NotFound if not found.
    *
    * @param versionString  VersionString
    * @param fn             Function to execute
    * @param request        Incoming request
    * @param project        Project to get version from
    * @return               NotFound or function result
    */
  def withVersion(versionString: String)(fn: Version => Result)
                 (implicit request: Request[_], project: Project): Future[Result]
  = project.versions.find(equalsIgnoreCase[VersionTable](_.versionString, versionString)).map(_.map(fn).getOrElse(notFound()))

  def withVersionAsync(versionString: String)(fn: Version => Future[Result])
                 (implicit request: Request[_], project: Project): Future[Result]
  = project.versions.find(equalsIgnoreCase[VersionTable](_.versionString, versionString)).flatMap(_.map(fn).getOrElse(Future(notFound())))

  /** Ensures a request is authenticated */
  def Authenticated = Action andThen authAction

  /** Ensures a user's account is unlocked */
  def UserLock(redirect: Call = ShowHome) = Authenticated andThen userLock(redirect)

  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return       Request with a project if found, NotFound otherwise.
    */
  def ProjectAction(author: String, slug: String) = Action andThen projectAction(author, slug)

  /**
    * Retrieves, processes, and adds a [[Project]] to a request.
    *
    * @param pluginId The project's unique plugin ID
    * @return         Request with a project if found, NotFound otherwise
    */
  def ProjectAction(pluginId: String) = Action andThen projectAction(pluginId)

  /**
    * Ensures a request is authenticated and retrieves, processes, and adds a
    * [[Project]] to a request.
    *
    * @param author Project owner
    * @param slug Project slug
    * @return Authenticated request with a project if found, NotFound otherwise.
    */
  def AuthedProjectAction(author: String, slug: String, requireUnlock: Boolean = false) = {
    val first = if (requireUnlock) UserLock(ShowProject(author, slug)) else Authenticated
    first andThen authedProjectAction(author, slug)
  }

  def AuthedProjectActionById(pluginId: String, requireUnlock: Boolean = true) = {
    val first = if (requireUnlock) UserLock(ShowProject(pluginId)) else Authenticated
    first andThen authedProjectActionById(pluginId)
  }

  /**
    * Retrieves an [[models.user.Organization]] and adds it to the request.
    *
    * @param organization Organization to retrieve
    * @return             Request with organization if found, NotFound otherwise
    */
  def OrganizationAction(organization: String) = Action andThen organizationAction(organization)

  /**
    * Ensures a request is authenticated and retrieves and adds a
    * [[models.user.Organization]] to the request.
    *
    * @param organization Organization to retrieve
    * @return             Authenticated request with Organization if found, NotFound otherwise
    */
  def AuthedOrganizationAction(organization: String, requireUnlock: Boolean = false) = {
    val first = if (requireUnlock) UserLock(ShowUser(organization)) else Authenticated
    first andThen authedOrganizationAction(organization)
  }

  /**
    * A request that ensures that a user has permission to edit a specified
    * profile.
    *
    * @param username User to check
    * @return [[AuthRequest]] if has permission
    */
  def UserAction(username: String) = Authenticated andThen userAction(username)

  /**
    * Represents an action that requires a user to reenter their password.
    *
    * @param username Username to verify
    * @param sso      Incoming SSO payload
    * @param sig      Incoming SSO signature
    * @return         None if verified, Unauthorized otherwise
    */
  def VerifiedAction(username: String, sso: Option[String], sig: Option[String])
  = UserAction(username) andThen verifiedAction(sso, sig)

}
