package models.viewhelper

import db.ModelService
import models.user.role.OrganizationRole
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.permission._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

import util.functional.OptionT
import util.instances.future._

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
      members <- orga.memberships.members
      memberRoles <- Future.sequence(members.map(_.headRole))
      memberUser <- Future.sequence(memberRoles.map(_.user))
    } yield {
      val members = memberRoles zip memberUser
      OrganizationData(orga, role, members.toSeq)
    }
  }


  def of[A](orga: Option[Organization])(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext,
                                        service: ModelService): OptionT[Future, OrganizationData] = {
    OptionT.fromOption[Future](orga).semiFlatMap(of)
  }
}
