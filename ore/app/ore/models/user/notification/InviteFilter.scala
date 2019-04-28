package ore.models.user.notification

import scala.language.higherKinds

import scala.collection.immutable

import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.User
import ore.models.user.role.{OrganizationUserRole, ProjectUserRole, UserRoleModel}
import ore.db.access.ModelView
import ore.db.{Model, ModelService}

import cats.Parallel
import enumeratum.values._

/**
  * A collection of ways to filter invites.
  */
sealed abstract class InviteFilter(
    val value: Int,
    val name: String,
    val title: String,
    val filter: InviteFilter.FilterFunc
) extends IntEnumEntry {
  def apply[F[_], G[_]](
      user: Model[User]
  )(implicit service: ModelService[F], par: Parallel[F, G]): F[Seq[Model[UserRoleModel[_]]]] =
    filter(user)
}

object InviteFilter extends IntEnum[InviteFilter] {
  trait FilterFunc {
    def apply[F[_], G[_]](
        user: Model[User]
    )(implicit service: ModelService[F], par: Parallel[F, G]): F[Seq[Model[UserRoleModel[_]]]]
  }

  val values: immutable.IndexedSeq[InviteFilter] = findValues

  case object All
      extends InviteFilter(
        0,
        "all",
        "notification.invite.all",
        new FilterFunc {
          override def apply[F[_], G[_]](
              user: Model[User]
          )(implicit service: ModelService[F], par: Parallel[F, G]): F[Seq[Model[UserRoleModel[_]]]] =
            Parallel.parMap2(
              service.runDBIO(user.projectRoles(ModelView.raw(ProjectUserRole)).filter(!_.isAccepted).result),
              service.runDBIO(
                user.organizationRoles(ModelView.raw(OrganizationUserRole)).filter(!_.isAccepted).result
              )
            )(_ ++ _)
        }
      )

  case object Projects
      extends InviteFilter(
        1,
        "projects",
        "notification.invite.projects",
        new FilterFunc {
          override def apply[F[_], G[_]](
              user: Model[User]
          )(implicit service: ModelService[F], par: Parallel[F, G]): F[Seq[Model[UserRoleModel[_]]]] =
            service.runDBIO(user.projectRoles(ModelView.raw(ProjectUserRole)).filter(!_.isAccepted).result)
        }
      )

  case object Organizations
      extends InviteFilter(
        2,
        "organizations",
        "notification.invite.organizations",
        new FilterFunc {
          override def apply[F[_], G[_]](
              user: Model[User]
          )(implicit service: ModelService[F], par: Parallel[F, G]): F[Seq[Model[UserRoleModel[_]]]] =
            service.runDBIO(user.organizationRoles(ModelView.raw(OrganizationUserRole)).filter(!_.isAccepted).result)
        }
      )
}
