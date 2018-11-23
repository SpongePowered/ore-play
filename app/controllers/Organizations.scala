package controllers

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}

import controllers.sugar.Bakery
import db.{DbRef, ModelService}
import form.OreForms
import form.organization.{OrganizationMembersUpdate, OrganizationRoleSetBuilder}
import models.user.role.OrganizationUserRole
import ore.permission.EditSettings
import ore.user.MembershipDossier
import ore.user.MembershipDossier._
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.syntax._
import views.{html => views}

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._

/**
  * Controller for handling Organization based actions.
  */
class Organizations @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    config: OreConfig,
    service: ModelService,
    cache: AsyncCacheApi,
    messagesApi: MessagesApi
) extends OreBaseController {

  private def EditOrganizationAction(organization: String) =
    AuthedOrganizationAction(organization, requireUnlock = true).andThen(OrganizationPermissionAction(EditSettings))

  private val createLimit: Int = this.config.ore.orgs.createLimit

  /**
    * Shows the creation panel for Organizations.
    *
    * @return Organization creation panel
    */
  def showCreator(): Action[AnyContent] = UserLock().asyncF { implicit request =>
    request.user.ownedOrganizations.size.map { size =>
      if (size >= this.createLimit)
        Redirect(ShowHome).withError(request.messages.apply("error.org.createLimit", this.createLimit))
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
  def create(): Action[OrganizationRoleSetBuilder] =
    UserLock().asyncF(
      parse.form(forms.OrganizationCreate, onErrors = FormErrorLocalized(routes.Organizations.showCreator()))
    ) { implicit request =>
      val user     = request.user
      val failCall = routes.Organizations.showCreator()
      user.ownedOrganizations.size.flatMap { size =>
        if (size >= this.createLimit)
          IO.pure(BadRequest)
        else if (user.isLocked)
          IO.pure(Redirect(failCall).withError("error.user.locked"))
        else if (!this.config.ore.orgs.enabled)
          IO.pure(Redirect(failCall).withError("error.org.disabled"))
        else {
          val formData = request.body
          organizations
            .create(formData.name, user.id.value, formData.build())
            .bimap(
              error => Redirect(failCall).withError(error),
              organization => Redirect(routes.Users.showProjects(organization.name, None))
            )
            .merge
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
  def setInviteStatus(id: DbRef[OrganizationUserRole], status: String): Action[AnyContent] =
    Authenticated.asyncF { implicit request =>
      request.user.organizationRoles
        .get(id)
        .semiflatMap { role =>
          status match {
            case STATUS_DECLINE  => role.organization.flatMap(MembershipDossier.organization.removeRole(_, role)).as(Ok)
            case STATUS_ACCEPT   => service.update(role.copy(isAccepted = true)).as(Ok)
            case STATUS_UNACCEPT => service.update(role.copy(isAccepted = false)).as(Ok)
            case _               => IO.pure(BadRequest)
          }
        }
        .getOrElse(notFound)
    }

  /**
    * Updates an [[models.user.Organization]]'s avatar.
    *
    * @param organization Organization to update avatar of
    * @return             Json response with errors if any
    */
  def updateAvatar(organization: String): Action[AnyContent] = EditOrganizationAction(organization) {
    implicit request =>
      // TODO implement me
      Ok
  }

  /**
    * Removes a member from an [[models.user.Organization]].
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def removeMember(organization: String): Action[String] =
    EditOrganizationAction(organization).asyncF(parse.form(forms.OrganizationMemberRemove)) { implicit request =>
      val res = for {
        user <- users.withName(request.body)
        _    <- OptionT.liftF(request.data.orga.memberships.removeMember(request.data.orga, user))
      } yield Redirect(ShowUser(organization))

      res.getOrElse(BadRequest)
    }

  /**
    * Updates an [[models.user.Organization]]'s members.
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def updateMembers(organization: String): Action[OrganizationMembersUpdate] =
    EditOrganizationAction(organization)(parse.form(forms.OrganizationUpdateMembers)) { implicit request =>
      request.body.saveTo(request.data.orga)
      Redirect(ShowUser(organization))
    }
}
