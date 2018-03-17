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
                            members: Seq[(OrganizationMember, OrganizationRole, User)], // TODO sorted/reverse
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

    Future.successful {
      OrganizationData(request.data, orga, null, null, noPerms) // TODO fill
    }



  }
}
