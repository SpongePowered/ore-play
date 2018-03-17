package models.viewhelper

import controllers.sugar.Requests.OreRequest
import db.ModelService
import models.project.VisibilityTypes
import models.user.{Organization, User}
import models.user.role.OrganizationRole
import ore.organization.OrganizationMember
import ore.permission._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in orga ;


case class OrganizationData(headerData: HeaderData,
                            joinable: Organization,
                            ownerRole: OrganizationRole,
                            members: Seq[(OrganizationRole, User)], // TODO sorted/reverse
                            permissions: Map[Permission, Boolean])
  extends JoinableData[OrganizationRole, OrganizationMember, Organization] {

  def orga: Organization = joinable

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser

}


object OrganizationData {
  val noPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def of[A](request: OreRequest[A], orga: Organization)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[OrganizationData] = {
    // TODO cache
    implicit val users = orga.userBase
    for {
      editSettings <- request.data.currentUser.get can EditSettings in orga map ((EditSettings, _))
      role <- orga.owner.headRole
      memberRoles <- orga.memberships.members.flatMap(m => Future.sequence(m.map(_.headRole)))
      memberUser <- Future.sequence(memberRoles.map(_.user))
    } yield {

      val perms: Map[Permission, Boolean] = Seq(editSettings).toMap
      val members = memberRoles zip memberUser
      OrganizationData(request.data,
        orga,
        role,
        members.toSeq,
        perms)
    }
  }
}
