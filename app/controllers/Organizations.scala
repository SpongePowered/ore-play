package controllers

import controllers.sugar.Bakery
import db.ModelService
import discourse.OreDiscourseApi
import form.OreForms
import javax.inject.Inject
import ore.permission.EditSettings
import ore.rest.OreWrites
import ore.user.MembershipDossier._
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import views.{html => views}
import util.instances.future._
import util.functional.Id

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{Action, AnyContent}

/**
  * Controller for handling Organization based actions.
  */
class Organizations @Inject()(forms: OreForms,
                              writes: OreWrites,
                              forums: OreDiscourseApi,
                              implicit override val bakery: Bakery,
                              implicit override val sso: SingleSignOnConsumer,
                              implicit override val env: OreEnv,
                              implicit override val config: OreConfig,
                              implicit override val service: ModelService,
                              implicit override val cache: AsyncCacheApi,
                              implicit override val messagesApi: MessagesApi)(implicit val ec: ExecutionContext) extends OreBaseController {

  private def EditOrganizationAction(organization: String)
  = AuthedOrganizationAction(organization, requireUnlock = true) andThen OrganizationPermissionAction(EditSettings)

  private val createLimit: Int = this.config.orgs.get[Int]("createLimit")

  /**
    * Shows the creation panel for Organizations.
    *
    * @return Organization creation panel
    */
  def showCreator(): Action[AnyContent] = UserLock().async { implicit request =>
    request.user.ownedOrganizations.size.map { size =>
      if (size >= this.createLimit) Redirect(ShowHome).withError(request.messages.apply("error.org.createLimit", this.createLimit))
      else {
        Ok(views.createOrganization())
      }
    }

  }

  /**
    * Creates a new organization from the submitted data.
    *
    * @return Redirect to organization page
    */
  def create(): Action[AnyContent] = UserLock().async { implicit request =>
    val user = request.user
    val failCall = routes.Organizations.showCreator()
    user.ownedOrganizations.size.flatMap { size =>
      if (size >= this.createLimit)
        Future.successful(BadRequest)
      else if (user.isLocked)
        Future.successful(Redirect(failCall).withError("error.user.locked"))
      else if (!this.config.orgs.get[Boolean]("enabled"))
        Future.successful(Redirect(failCall).withError("error.org.disabled"))
      else {
        bindFormEitherT[Future](this.forms.OrganizationCreate)(hasErrors => FormError(failCall, hasErrors)).flatMap { formData =>
          this.organizations.create(formData.name, user.id.value, formData.build()).bimap(
            error => Redirect(failCall).withError(error),
            organization => Redirect(routes.Users.showProjects(organization.name, None))
          )
        }.merge
      }
    }
  }

  /**
    * Sets the status of a pending Organization invite for the current user.
    *
    * @param id     Invite ID
    * @param status Invite status
    * @return       NotFound if invite doesn't exist, Ok otherwise
    */
  def setInviteStatus(id: Int, status: String): Action[AnyContent] = Authenticated.async { implicit request =>
    val user = request.user
    user.organizationRoles.get(id).map { role =>
      status match {
        case STATUS_DECLINE =>
          role.organization.foreach(_.memberships.removeRole(role))
          Ok
        case STATUS_ACCEPT =>
          role.setAccepted(true)
          Ok
        case STATUS_UNACCEPT =>
          role.setAccepted(false)
          Ok
        case _ =>
          BadRequest
      }
    }.getOrElse(notFound)
  }

  /**
    * Updates an [[models.user.Organization]]'s avatar.
    *
    * @param organization Organization to update avatar of
    * @return             Json response with errors if any
    */
  def updateAvatar(organization: String): Action[AnyContent] = EditOrganizationAction(organization) { implicit request =>
    // TODO implement me
    Ok
  }

  /**
    * Removes a member from an [[models.user.Organization]].
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def removeMember(organization: String): Action[AnyContent] = EditOrganizationAction(organization).async { implicit request =>
    val res = for {
      name <- bindFormOptionT[Future](this.forms.OrganizationMemberRemove)
      user <- this.users.withName(name)
    } yield {
      request.data.orga.memberships.removeMember(user)
      Redirect(ShowUser(organization))
    }

    res.getOrElse(BadRequest)
  }

  /**
    * Updates an [[models.user.Organization]]'s members.
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def updateMembers(organization: String): Action[AnyContent] = EditOrganizationAction(organization) { implicit request =>
    bindFormOptionT[Id](this.forms.OrganizationUpdateMembers).map { update =>
      update.saveTo(request.data.orga)
      Redirect(ShowUser(organization))
    }.getOrElse(BadRequest)
  }

}
