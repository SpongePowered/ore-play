package ore.user.notification

import scala.collection.immutable

import db.ModelService
import models.user.User
import models.user.role.UserRoleModel

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
    val filter: ContextShift[IO] => ModelService => User => IO[Seq[UserRoleModel]]
) extends IntEnumEntry {
  def apply(user: User)(implicit service: ModelService, cs: ContextShift[IO]): IO[Seq[UserRoleModel]] =
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
                user.projectRoles.filterNotNow(_.isAccepted),
                user.organizationRoles.filterNotNow(_.isAccepted)
              )(_ ++ _)
      )

  case object Projects
      extends InviteFilter(
        1,
        "projects",
        "notification.invite.projects",
        _ => implicit service => user => user.projectRoles.filterNotNow(_.isAccepted)
      )

  case object Organizations
      extends InviteFilter(
        2,
        "organizations",
        "notification.invite.organizations",
        _ => implicit service => user => user.organizationRoles.filterNotNow(_.isAccepted)
      )
}
