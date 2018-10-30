package form.organization

import scala.concurrent.{ExecutionContext, Future}

import db.{DbRef, ModelService}
import models.user.role.OrganizationRole
import models.user.{Notification, Organization, User}
import ore.OreConfig
import ore.permission.role.RoleType
import ore.user.notification.NotificationType

import cats.data.NonEmptyList

/**
  * Saves new and old [[OrganizationRole]]s.
  *
  * @param users    New users
  * @param roles    New roles
  * @param userUps  Old users
  * @param roleUps  Old roles
  */
case class OrganizationMembersUpdate(
    users: List[DbRef[User]],
    roles: List[String],
    userUps: List[String],
    roleUps: List[String]
) extends TOrganizationRoleSetBuilder {

  //noinspection ComparingUnrelatedTypes
  def saveTo(organization: Organization)(
      implicit ex: ExecutionContext,
      service: ModelService,
      config: OreConfig
  ): Unit = {
    if (!organization.isDefined)
      throw new RuntimeException("tried to update members on undefined organization")

    // Add new roles
    val dossier = organization.memberships
    val orgId   = organization.id.value
    for (role <- this.build()) {
      val user = role.user
      dossier.addRole(organization, role.copy(organizationId = orgId))
      user.flatMap { user =>
        user.sendNotification(
          Notification(
            userId = user.id.value,
            originId = orgId,
            notificationType = NotificationType.OrganizationInvite,
            messageArgs = NonEmptyList.of("notification.organization.invite", role.roleType.title, organization.name)
          )
        )
      }
    }

    // Update existing roles
    val orgRoleTypes = RoleType.values.filter(_.roleClass.equals(classOf[OrganizationRole]))
    for ((user, i) <- this.userUps.zipWithIndex) {
      organization.memberships
        .members(organization)
        .flatMap { members =>
          Future.sequence(members.map(member => member.user.map((_, member))))
        }
        .map { users =>
          users.find(_._1.name.equalsIgnoreCase(user.trim)).foreach { user =>
            user._2.headRole.flatMap { role =>
              val roleType = orgRoleTypes
                .find(_.title.equals(roleUps(i)))
                .getOrElse(throw new RuntimeException("supplied invalid role type"))
              service.update(role.copy(roleType = roleType))
            }
          }
        }
    }
  }
}
