package ore.user.notification

import scala.collection.immutable

import db.ModelService
import models.user.User
import models.user.role.UserRoleModel

import cats.effect.IO
import cats.syntax.all._
import enumeratum.values._

/**
  * A collection of ways to filter invites.
  */
sealed abstract class InviteFilter(
    val value: Int,
    val name: String,
    val title: String,
    val filter: ModelService => User => IO[Seq[UserRoleModel]]
) extends IntEnumEntry {
  def apply(user: User)(implicit service: ModelService): IO[Seq[UserRoleModel]] =
    filter(service)(user)
}

object InviteFilter extends IntEnum[InviteFilter] {

  val values: immutable.IndexedSeq[InviteFilter] = findValues

  case object All
      extends InviteFilter(
        0,
        "all",
        "notification.invite.all",
        implicit service =>
          user => user.projectRoles.filterNot(_.isAccepted).map2(user.organizationRoles.filterNot(_.isAccepted))(_ ++ _)
      )

  case object Projects
      extends InviteFilter(
        1,
        "projects",
        "notification.invite.projects",
        implicit service => user => user.projectRoles.filterNot(_.isAccepted)
      )

  case object Organizations
      extends InviteFilter(
        2,
        "organizations",
        "notification.invite.organizations",
        implicit service => user => user.organizationRoles.filterNot(_.isAccepted)
      )
}
