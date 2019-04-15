package models.viewhelper

import models.user.{Organization, User}
import ore.db.{Model, ModelService}
import ore.permission.Permission

import cats.data.OptionT
import cats.effect.{ContextShift, IO}

case class ScopedOrganizationData(permissions: Permission = Permission.None)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Model[Organization], user: Model[User]) = s"""organization${orga.id}foruser${user.id}"""

  def of[A](currentUser: Option[Model[User]], orga: Model[Organization])(
      implicit service: ModelService
  ): IO[ScopedOrganizationData] =
    currentUser.fold(IO.pure(noScope))(_.permissionsIn(orga).map(ScopedOrganizationData(_)))

  def of[A](currentUser: Option[Model[User]], orga: Option[Model[Organization]])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): OptionT[IO, ScopedOrganizationData] =
    OptionT.fromOption[IO](orga).semiflatMap(of(currentUser, _))
}
