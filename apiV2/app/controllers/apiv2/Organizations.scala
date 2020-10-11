package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, Result}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, Members}
import db.impl.query.APIV2Queries
import ore.data.user.notification.NotificationType
import ore.db.impl.schema.OrganizationRoleTable
import ore.models.organization.Organization
import ore.models.user.role.OrganizationUserRole
import ore.permission.Permission

import io.circe.syntax._
import zio.interop.catz._
import zio._

class Organizations(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {

  def showOrganization(organization: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF {
      service
        .runDbCon(APIV2Queries.organizationQuery(organization).option)
        .map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
    }

  def showMembers(organization: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.OrganizationScope(organization)).asyncF { implicit r =>
      Members.membersAction(APIV2Queries.orgaMembers(organization, _, _), limit, offset)
    }

  def updateMembers(organization: String): Action[List[Members.MemberUpdate]] =
    ApiAction(Permission.ManageOrganizationMembers, APIScope.OrganizationScope(organization))
      .asyncF(parseCirce.decodeJson[List[Members.MemberUpdate]]) { implicit r =>
        Members.updateMembers[Organization, OrganizationUserRole, OrganizationRoleTable](
          getSubject = organizations.withName(organization).someOrFail(NotFound),
          allowOrgMembers = false,
          getMembersQuery = APIV2Queries.orgaMembers(organization, _, _),
          createRole = OrganizationUserRole(_, _, _),
          roleCompanion = OrganizationUserRole,
          notificationType = NotificationType.OrganizationInvite,
          notificationLocalization = "notification.organization.invite"
        )
      }
}
