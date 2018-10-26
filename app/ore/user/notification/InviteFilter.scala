package ore.user.notification

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.user.User
import models.user.role.RoleModel

import cats.instances.future._
import cats.syntax.all._
import enumeratum.values._

/**
  * A collection of ways to filter invites.
  */
sealed abstract class InviteFilter(
    val value: Int,
    val name: String,
    val title: String,
    val filter: ExecutionContext => ModelService => User => Future[Seq[RoleModel]]
) extends IntEnumEntry {
  def apply(user: User)(implicit ec: ExecutionContext, service: ModelService): Future[Seq[RoleModel]] =
    filter(ec)(service)(user)
}

object InviteFilter extends IntEnum[InviteFilter] {

  val values: immutable.IndexedSeq[InviteFilter] = findValues

  case object All
      extends InviteFilter(
        0,
        "all",
        "notification.invite.all",
        implicit ec =>
          implicit service =>
            user =>
              user.projectRoles.filterNot(_.isAccepted).map2(user.organizationRoles.filterNot(_.isAccepted))(_ ++ _)
      )

  case object Projects
      extends InviteFilter(
        1,
        "projects",
        "notification.invite.projects",
        implicit ec => implicit service => user => user.projectRoles.filterNot(_.isAccepted)
      )

  case object Organizations
      extends InviteFilter(
        2,
        "organizations",
        "notification.invite.organizations",
        implicit ec => implicit service => user => user.organizationRoles.filterNot(_.isAccepted)
      )
}
