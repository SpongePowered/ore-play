package form.organization

import db.{DbRef, ModelService}
import models.user.role.OrganizationUserRole
import models.user.{Notification, Organization, User}
import ore.OreConfig
import ore.permission.role.Role
import ore.user.notification.NotificationType
import util.syntax._

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Saves new and old [[OrganizationUserRole]]s.
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
      implicit service: ModelService,
      config: OreConfig,
      cs: ContextShift[IO]
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
            messageArgs = NonEmptyList.of("notification.organization.invite", role.role.title, organization.name)
          )
        )
      }
    }

    // Update existing roles
    for ((user, i) <- this.userUps.zipWithIndex) {
      organization.memberships
        .members(organization)
        .flatMap { members =>
          import cats.instances.vector._
          members.toVector.parTraverse(mem => mem.user.tupleRight(mem))
        }
        .map { users =>
          users.find(_._1.name.equalsIgnoreCase(user.trim)).foreach { user =>
            user._2.headRole.flatMap { role =>
              val roleType = Role.organizationRoles
                .find(_.title.equals(roleUps(i)))
                .getOrElse(throw new RuntimeException("supplied invalid role type"))
              service.update(role.copy(role = roleType))
            }
          }
        }
    }
  }
}
