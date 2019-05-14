package controllers

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent}

import controllers.sugar.Bakery
import ore.db.impl.OrePostgresDriver.api._
import form.OreForms
import form.organization.{OrganizationMembersUpdate, OrganizationRoleSetBuilder}
import ore.models.user.role.OrganizationUserRole
import ore.db.access.ModelView
import ore.db.{DbRef, ModelService}
import ore.member.MembershipDossier
import ore.models.organization.Organization
import ore.permission.Permission
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
    service: ModelService[IO],
    messagesApi: MessagesApi
) extends OreBaseController {

  private val createLimit: Int = this.config.ore.orgs.createLimit

  /**
    * Shows the creation panel for Organizations.
    *
    * @return Organization creation panel
    */
  def showCreator(): Action[AnyContent] = UserLock().asyncF { implicit request =>
    service
      .runDBIO((request.user.ownedOrganizations(ModelView.later(Organization)).size > this.createLimit).result)
      .map { limitReached =>
        if (limitReached)
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

      if (user.isLocked) {
        IO.pure(BadRequest)
      } else if (!this.config.ore.orgs.enabled) {
        IO.pure(Redirect(failCall).withError("error.org.disabled"))
      } else {
        service
          .runDBIO((user.ownedOrganizations(ModelView.later(Organization)).size >= this.createLimit).result)
          .flatMap { limitReached =>
            if (limitReached)
              IO.pure(BadRequest)
            else {
              val formData = request.body
              organizations
                .create(formData.name, user.id, formData.build())
                .fold(
                  error => Redirect(failCall).withErrors(error),
                  organization => Redirect(routes.Users.showProjects(organization.name, None))
                )
            }
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
      request.user
        .organizationRoles(ModelView.now(OrganizationUserRole))
        .get(id)
        .semiflatMap { role =>
          import MembershipDossier._
          status match {
            case STATUS_DECLINE  => role.organization.flatMap(org => org.memberships.removeRole(org)(role.id)).as(Ok)
            case STATUS_ACCEPT   => service.update(role)(_.copy(isAccepted = true)).as(Ok)
            case STATUS_UNACCEPT => service.update(role)(_.copy(isAccepted = false)).as(Ok)
            case _               => IO.pure(BadRequest)
          }
        }
        .getOrElse(notFound)
    }

  /**
    * Updates an [[Organization]]'s avatar.
    *
    * @param organization Organization to update avatar of
    * @return             Redirect to auth or bad request
    */
  def updateAvatar(organization: String): Action[AnyContent] =
    AuthedOrganizationAction(organization, requireUnlock = true)
      .andThen(OrganizationPermissionAction(Permission.EditOrganizationSettings))
      .asyncF { implicit request =>
        implicit val lang: Lang = request.lang

        auth.getChangeAvatarToken(request.user.name, organization).value.map {
          case Left(_) =>
            Redirect(routes.Users.showProjects(organization, None)).withError(messagesApi("organization.avatarFailed"))
          case Right(token) =>
            Redirect(auth.url + s"/accounts/user/$organization/change-avatar/?key=${token.signedData}")
        }
      }

  /**
    * Removes a member from an [[Organization]].
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def removeMember(organization: String): Action[String] =
    AuthedOrganizationAction(organization, requireUnlock = true)
      .andThen(OrganizationPermissionAction(Permission.ManageOrganizationMembers))
      .asyncF(parse.form(forms.OrganizationMemberRemove)) { implicit request =>
        val res = for {
          user <- users.withName(request.body)
          _    <- OptionT.liftF(request.data.orga.memberships.removeMember(request.data.orga)(user.id))
        } yield Redirect(ShowUser(organization))

        res.getOrElse(BadRequest)
      }

  /**
    * Updates an [[Organization]]'s members.
    *
    * @param organization Organization to update
    * @return             Redirect to Organization page
    */
  def updateMembers(organization: String): Action[OrganizationMembersUpdate] =
    AuthedOrganizationAction(organization, requireUnlock = true)
      .andThen(OrganizationPermissionAction(Permission.ManageOrganizationMembers))(
        parse.form(forms.OrganizationUpdateMembers)
      )
      .asyncF { implicit request =>
        request.body.saveTo(request.data.orga).as(Redirect(ShowUser(organization)))
      }
}
