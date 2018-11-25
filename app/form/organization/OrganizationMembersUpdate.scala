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

  def saveTo(organization: Organization)(
      implicit service: ModelService,
      config: OreConfig,
      cs: ContextShift[IO]
  ): IO[Unit] = {
    import cats.instances.option._
    import cats.instances.vector._
    if (!organization.isDefined)
      throw new RuntimeException("tried to update members on undefined organization")

    // Add new roles
    val dossier = organization.memberships
    val orgId   = organization.id.value
    val addRoles = this
      .build()
      .toVector
      .parTraverse_ { role =>
        val addRole = dossier.addRole(organization, role.copy(organizationId = orgId))
        val sendNotif = role.user.flatMap { user =>
          user.sendNotification(
            Notification(
              userId = user.id.value,
              originId = orgId,
              notificationType = NotificationType.OrganizationInvite,
              messageArgs = NonEmptyList.of("notification.organization.invite", role.role.title, organization.name)
            )
          )
        }

        addRole *> sendNotif
      }

    val orgUsersF = organization.memberships
      .members(organization)
      .flatMap(members => members.toVector.parTraverse(mem => mem.user.tupleRight(mem)))

    val updateExisting = orgUsersF.flatMap { orgUsers =>
      val userMemRole = userUps.zip(roleUps).map {
        case (user, role) =>
          val roleType = Role.organizationRoles
            .find(_.title == role)
            .getOrElse(throw new Exception("supplied invalid role type"))
          orgUsers.find(_._1.name.equalsIgnoreCase(user.trim)).tupleRight(roleType)
      }

      userMemRole.toVector.parTraverse_ {
        case Some(((_, mem), role)) =>
          mem.headRole.flatMap(headRole => service.update(headRole.copy(role = role)))
        case None => IO.unit
      }
    }

    addRoles *> updateExisting
  }
}
