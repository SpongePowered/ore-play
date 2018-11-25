package models.viewhelper

import db.ModelService
import models.user.{Organization, User}
import ore.permission._

import cats.Parallel
import cats.data.OptionT
import cats.effect.{ContextShift, IO}

case class ScopedOrganizationData(permissions: Map[Permission, Boolean] = Map.empty)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Organization, user: User) = s"""organization${orga.id.value}foruser${user.id.value}"""

  def of[A](currentUser: Option[User], orga: Organization)(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[ScopedOrganizationData] =
    currentUser.fold(IO.pure(noScope)) { user =>
      Parallel.parMap2(user.trustIn(orga), user.globalRoles.allFromParent(user)) { (trust, globalRoles) =>
        ScopedOrganizationData(user.can.asMap(trust, globalRoles.toSet)(EditSettings))
      }
    }

  def of[A](currentUser: Option[User], orga: Option[Organization])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): OptionT[IO, ScopedOrganizationData] =
    OptionT.fromOption[IO](orga).semiflatMap(of(currentUser, _))
}
