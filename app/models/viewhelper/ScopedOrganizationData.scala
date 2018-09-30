package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.user.{Organization, User}
import ore.permission._

import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._

case class ScopedOrganizationData(permissions: Map[Permission, Boolean] = Map.empty)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Organization, user: User) = s"""organization${orga.id.value}foruser${user.id.value}"""

  def of[A](currentUser: Option[User], orga: Organization)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[ScopedOrganizationData] =
    currentUser.fold(Future.successful(noScope)) { user =>
      user
        .trustIn(orga)
        .map2(user.globalRoles.all)(
          (trust, globalRoles) => ScopedOrganizationData(user.can.asMap(trust, globalRoles)(EditSettings))
        )
    }

  def of[A](currentUser: Option[User], orga: Option[Organization])(
      implicit ec: ExecutionContext,
      service: ModelService
  ): OptionT[Future, ScopedOrganizationData] =
    OptionT.fromOption[Future](orga).semiflatMap(of(currentUser, _))
}
