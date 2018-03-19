package form.organization

import db.impl.access.UserBase
import models.user.role.OrganizationRole
import models.user.{Notification, Organization}
import models.viewhelper.HeaderData
import ore.permission.role.RoleTypes
import ore.user.notification.NotificationTypes
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Saves new and old [[OrganizationRole]]s.
  *
  * @param users    New users
  * @param roles    New roles
  * @param userUps  Old users
  * @param roleUps  Old roles
  */
case class OrganizationMembersUpdate(override val users: List[Int],
                                     override val roles: List[String],
                                     userUps: List[String],
                                     roleUps: List[String]) extends TOrganizationRoleSetBuilder {

  implicit val lang = Lang.defaultLang

  //noinspection ComparingUnrelatedTypes
  def saveTo(organization: Organization)(implicit cache: AsyncCacheApi, ex: ExecutionContext, messages: MessagesApi, users: UserBase) = {
    if (!organization.isDefined)
      throw new RuntimeException("tried to update members on undefined organization")

    // Add new roles
    val dossier = organization.memberships
    val orgId = organization.id.get
    for (role <- this.build()) {
      val user = role.user
      dossier.addRole(role.copy(organizationId = orgId))
      user.flatMap {
        _.sendNotification(Notification(
          originId = orgId,
          notificationType = NotificationTypes.OrganizationInvite,
          message = messages("notification.organization.invite", role.roleType.title, organization.name)
        ))
      }
    }

    // Update existing roles
    val orgRoleTypes = RoleTypes.values.filter(_.roleClass.equals(classOf[OrganizationRole]))
    for ((user, i) <- this.userUps.zipWithIndex) {
        organization.memberships.members.flatMap { members =>
          Future.sequence(members.map(member => member.user.map((_, member))))
        } map { users =>
          users.find(_._1.name.equalsIgnoreCase(user.trim)).foreach { user =>
            user._2.headRole.map { role =>
                role.setRoleType(orgRoleTypes.find(_.title.equals(roleUps(i))).getOrElse(throw new RuntimeException("supplied invalid role type")))
            }
          }
        }
      }
    }
}
