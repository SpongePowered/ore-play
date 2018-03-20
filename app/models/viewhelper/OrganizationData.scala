package models.viewhelper

import db.ModelService
import models.user.role.OrganizationRole
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.permission._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}


case class OrganizationData(joinable: Organization,
                            ownerRole: OrganizationRole,
                            members: Seq[(OrganizationRole, User)], // TODO sorted/reverse
                            )
  extends JoinableData[OrganizationRole, OrganizationMember, Organization] {

  def orga: Organization = joinable

}

object OrganizationData {
  val noPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def cacheKey(orga: Organization) = "organization" + orga.id.get

  def of[A](orga: Organization)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                service: ModelService): Future[OrganizationData] = {
    implicit val users = orga.userBase
    for {
      role <- orga.owner.headRole
      memberRoles <- orga.memberships.members.flatMap(m => Future.sequence(m.map(_.headRole)))
      memberUser <- Future.sequence(memberRoles.map(_.user))
    } yield {
      val members = memberRoles zip memberUser
      OrganizationData(orga, role, members.toSeq)
    }
  }


  def of[A](orga: Option[Organization])(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                        service: ModelService): Future[Option[OrganizationData]] = {
    orga match {
      case None => Future.successful(None)
      case Some(o) => of(o).map(Some(_))
    }
  }
}
