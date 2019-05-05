package models.viewhelper

import scala.language.higherKinds

import ore.db.{Model, ModelService}
import ore.models.organization.Organization
import ore.models.user.User
import ore.permission.Permission

import cats.data.OptionT
import cats.syntax.all._
import cats.{Applicative, Monad}

case class ScopedOrganizationData(permissions: Permission = Permission.None)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Model[Organization], user: Model[User]) = s"""organization${orga.id}foruser${user.id}"""

  def of[F[_]](currentUser: Option[Model[User]], orga: Model[Organization])(
      implicit service: ModelService[F],
      F: Applicative[F]
  ): F[ScopedOrganizationData] =
    currentUser.fold(F.pure(noScope))(_.permissionsIn(orga).map(ScopedOrganizationData(_)))

  def of[F[_]](currentUser: Option[Model[User]], orga: Option[Model[Organization]])(
      implicit service: ModelService[F],
      F: Monad[F]
  ): OptionT[F, ScopedOrganizationData] =
    OptionT.fromOption[F](orga).semiflatMap(of(currentUser, _))
}
