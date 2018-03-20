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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
                              implicit override val messagesApi: MessagesApi) extends OreBaseController {

  private def EditOrganizationAction(organization: String)
  = AuthedOrganizationAction(organization, requireUnlock = true) andThen OrganizationPermissionAction(EditSettings)

  private val createLimit: Int = this.config.orgs.get[Int]("createLimit")

  /**
    * Shows the creation panel for Organizations.
    *
    * @return Organization creation panel
    */
  def showCreator() = UserLock().async { implicit request =>
    request.user.ownedOrganizations.size.map { size =>
      if (size >= this.createLimit) Redirect(ShowHome).withError(this.messagesApi("error.org.createLimit", this.createLimit))
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
  def create() = UserLock().async { implicit request =>
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
        this.forms.OrganizationCreate.bindFromRequest().fold(
          hasErrors => Future.successful(FormError(failCall, hasErrors)),
          formData => {
            this.organizations.create(formData.name, user.id.get, formData.build()) map {
              case Left(error) => Redirect(failCall).withError(error)
              case Right(organization) =>
                Redirect(routes.Users.showProjects(organization.name, None))
            }
          }
        )
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
  def setInviteStatus(id: Int, status: String) = Authenticated.async { implicit request =>
    val user = request.user
    user.organizationRoles.get(id).flatMap {
      case None => Future.successful(notFound)
      case Some(role) =>
        role.organization.map { orga =>
          status match {
            case STATUS_DECLINE =>
              orga.memberships.removeRole(role)
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
        }

    }
  }

  /**
    * Updates an [[models.user.Organization]]'s avatar.
    *
    * @param organization Organization to update avatar of
    * @return             Json response with errors if any
    */
  def updateAvatar(organization: String) = EditOrganizationAction(organization) { implicit request =>
    // TODO implement me
    Ok
  }

  /**
    * Removes a member from an [[models.user.Organization]].
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def removeMember(organization: String) = EditOrganizationAction(organization).async { implicit request =>
    this.users.withName(this.forms.OrganizationMemberRemove.bindFromRequest.get.trim).map {
      case None => BadRequest
      case Some(user) =>
        request.data.orga.memberships.removeMember(user)
        Redirect(ShowUser(organization))
    }
  }

  /**
    * Updates an [[models.user.Organization]]'s members.
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def updateMembers(organization: String) = EditOrganizationAction(organization) { implicit request =>
    this.forms.OrganizationUpdateMembers.bindFromRequest.get.saveTo(request.data.orga)
    Redirect(ShowUser(organization))
  }

}
