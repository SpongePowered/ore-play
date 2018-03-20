package models.viewhelper

import db.ModelService
import models.user.{Organization, User}
import ore.permission.{Permission, _}
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

case class ScopedOrganizationData(permissions: Map[Permission, Boolean] = Map.empty)

object ScopedOrganizationData {

  val noScope = ScopedOrganizationData()

  def cacheKey(orga: Organization, user: User) = s"""organization${orga.id.get}foruser${user.id.get}"""

  def of[A](currentUser: Option[User], orga: Organization)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                                           service: ModelService): Future[ScopedOrganizationData] = {
    implicit val users = orga.userBase
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
                                                                   service: ModelService): Future[Option[ScopedOrganizationData]] = {
    orga match {
      case None => Future.successful(None)
      case Some(o) => of(currentUser, o).map(Some(_))
    }
  }
}