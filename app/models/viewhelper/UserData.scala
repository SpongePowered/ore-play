package models.viewhelper

import controllers.routes
import controllers.sugar.Requests.OreRequest
import db.ModelService
import models.user.role.OrganizationRole
import models.user.{Organization, User}
import ore.permission.{EditSettings, Permission, ReviewFlags, ViewActivity}
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

case class UserData(headerData: HeaderData,
                    user: User,
                    isOrga: Boolean,
                    projectCount: Int,
                    orgas: Seq[(Organization, OrganizationRole, User)],
                    userPerm: Map[Permission, Boolean],
                    orgaPerm: Map[Permission, Boolean]) {

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser

  def isCurrent = currentUser.contains(user)

  def pgpFormCall = {
    user.pgpPubKey.map { _ =>
      routes.Users.verify(Some(routes.Users.deletePgpPublicKey(user.name, None, None).path()))
    } getOrElse {
      routes.Users.savePgpPublicKey(user.name)
    }
  }

  def pgpFormClass = user.pgpPubKey.map(_ => "pgp-delete").getOrElse("")

}

object UserData {

  val noUserPerms: Map[Permission, Boolean] = Map(ViewActivity -> false, ReviewFlags -> false)
  val noOrgaPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def of[A](request: OreRequest[A], user: User)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[UserData] = {
    // TODO cache

    Future.successful {
      UserData(request.data, user, false, 0, Seq.empty, noUserPerms, noOrgaPerms)
    }

  }
}
