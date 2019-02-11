package ore.user.notification

import scala.collection.immutable

import db.{DbModel, ModelService}
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import models.user.User
import models.user.role.{OrganizationUserRole, ProjectUserRole, UserRoleModel}

import cats.Parallel
import cats.effect.{ContextShift, IO}
import enumeratum.values._

/**
  * A collection of ways to filter invites.
  */
sealed abstract class InviteFilter(
    val value: Int,
    val name: String,
    val title: String,
    val filter: ContextShift[IO] => ModelService => DbModel[User] => IO[Seq[DbModel[UserRoleModel]]]
) extends IntEnumEntry {
  def apply(
      user: DbModel[User]
  )(implicit service: ModelService, cs: ContextShift[IO]): IO[Seq[DbModel[UserRoleModel]]] =
    filter(cs)(service)(user)
}

object InviteFilter extends IntEnum[InviteFilter] {

  val values: immutable.IndexedSeq[InviteFilter] = findValues

  case object All
      extends InviteFilter(
        0,
        "all",
        "notification.invite.all",
        implicit cs =>
          implicit service =>
            user =>
              Parallel.parMap2(
                service.runDBIO(user.projectRoles(ModelView.raw(ProjectUserRole)).filter(!_.isAccepted).result),
                service.runDBIO(
                  user.organizationRoles(ModelView.raw(OrganizationUserRole)).filter(!_.isAccepted).result
                )
              )(_ ++ _)
      )

  case object Projects
      extends InviteFilter(
        1,
        "projects",
        "notification.invite.projects",
        _ =>
          implicit service =>
            user => service.runDBIO(user.projectRoles(ModelView.raw(ProjectUserRole)).filter(!_.isAccepted).result)
      )

  case object Organizations
      extends InviteFilter(
        2,
        "organizations",
        "notification.invite.organizations",
        _ =>
          implicit service =>
            user =>
              service.runDBIO(user.organizationRoles(ModelView.raw(OrganizationUserRole)).filter(!_.isAccepted).result)
      )
}
