package models.viewhelper

import db.ModelService
import models.user.{Organization, User}
import ore.permission.{Permission, _}
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import util.functional.OptionT
import util.instances.future._

case class ScopedOrganizationData(permissions: Map[Permission, Boolean] = Map.empty)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Organization, user: User) = s"""organization${orga.id.value}foruser${user.id.value}"""

  def of[A](currentUser: Option[User], orga: Organization)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                                           service: ModelService): Future[ScopedOrganizationData] = {
    implicit val users: UserBase = orga.userBase
    if (currentUser.isEmpty) Future.successful(noScope)
    else {
      for {
        editSettings <- currentUser.get can EditSettings in orga map ((EditSettings, _))
      } yield {

        val perms: Map[Permission, Boolean] = Seq(editSettings).toMap
        ScopedOrganizationData(perms)
      }
    }
  }

  def of[A](currentUser: Option[User], orga: Option[Organization])(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                                                   service: ModelService): OptionT[Future, ScopedOrganizationData] = {
    OptionT.fromOption[Future](orga).semiFlatMap(of(currentUser, _))
  }
}
