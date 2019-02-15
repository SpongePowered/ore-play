package models.viewhelper

import db.{DbModel, ModelService}
import models.user.{Organization, User}
import ore.permission._

import cats.Parallel
import cats.data.OptionT
import cats.effect.{ContextShift, IO}

case class ScopedOrganizationData(permissions: Map[Permission, Boolean] = Map.empty)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: DbModel[Organization], user: DbModel[User]) = s"""organization${orga.id}foruser${user.id}"""

  def of[A](currentUser: Option[DbModel[User]], orga: DbModel[Organization])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[ScopedOrganizationData] =
    currentUser.fold(IO.pure(noScope)) { user =>
      Parallel.parMap2(user.trustIn(orga), user.globalRoles.allFromParent) { (trust, globalRoles) =>
        ScopedOrganizationData(user.can.asMap(trust, globalRoles.toSet)(EditSettings))
      }
    }

  def of[A](currentUser: Option[DbModel[User]], orga: Option[DbModel[Organization]])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): OptionT[IO, ScopedOrganizationData] =
    OptionT.fromOption[IO](orga).semiflatMap(of(currentUser, _))
}
