package form.organization

import scala.language.higherKinds

import ore.data.user.notification.NotificationType
import ore.db.access.ModelView
import ore.models.user.role.OrganizationUserRole
import ore.models.user.{Notification, User}
import ore.db.{DbRef, Model, ModelService}
import ore.models.organization.Organization
import ore.permission.role.Role
import util.syntax._

import cats.{MonadError, Parallel}
import cats.data.NonEmptyList
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

  def saveTo[F[_], G[_]](organization: Model[Organization])(
      implicit service: ModelService[F],
      F: MonadError[F, Throwable],
      par: Parallel[F, G]
  ): F[Unit] = {
    import cats.instances.list._
    import cats.instances.option._
    import cats.instances.vector._

    // Add new roles
    val dossier = organization.memberships
    val orgId   = organization.id
    val addRoles = this
      .build()
      .toVector
      .parTraverse_ { role =>
        val addRole = dossier.addRole(organization)(role.userId, role.copy(organizationId = orgId))
        val sendNotif = service.insert(
          Notification(
            userId = role.userId,
            originId = Some(orgId),
            notificationType = NotificationType.OrganizationInvite,
            messageArgs = NonEmptyList.of("notification.organization.invite", role.role.title, organization.name)
          )
        )

        addRole *> sendNotif
      }

    val orgUsersF = organization.memberships
      .members(organization)
      .flatMap { members =>
        members.toVector.parTraverse { mem =>
          ModelView
            .now(User)
            .get(mem)
            .getOrElseF(F.raiseError(new Exception("Could not find member for organization")))
        }
      }

    val roleObjUpsF = roleUps.traverse { role =>
      Role.organizationRoles
        .find(_.value == role)
        .fold(F.raiseError[Role](new Exception(s"Supplied invalid role type: $role")))(F.pure)
    }

    val updateExisting = (roleObjUpsF, orgUsersF).tupled.flatMap {
      case (roleObjUps, orgUsers) =>
        val userMemRole = userUps.zip(roleObjUps).map {
          case (user, roleType) => orgUsers.find(_.name.equalsIgnoreCase(user.trim)).tupleRight(roleType)
        }

        userMemRole.toVector.parTraverse_ {
          case Some((mem, role)) =>
            organization.memberships.getRoles(organization)(mem.id).flatMap { roles =>
              roles.toVector.parTraverse_(userRole => service.update(userRole)(_.copy(role = role)))
            }
          case None => F.unit
        }
    }

    addRoles *> updateExisting
  }
}
