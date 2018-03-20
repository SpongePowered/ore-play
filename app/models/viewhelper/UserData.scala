package models.viewhelper

import controllers.routes
import controllers.sugar.Requests.OreRequest
import db.ModelService
import models.user.role.OrganizationRole
import models.user.{Organization, User}
import ore.permission._
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

// TODO separate Scoped UserData

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
      routes.Users.verify(Some(routes.Users.deletePgpPublicKey(user.name, None, None).path))
    } getOrElse {
      routes.Users.savePgpPublicKey(user.name)
    }
  }

  def pgpFormClass = user.pgpPubKey.map(_ => "pgp-delete").getOrElse("")

}

object UserData {

  val noUserPerms: Map[Permission, Boolean] = Map(ViewActivity -> false, ReviewFlags -> false, ReviewProjects -> false)
  val noOrgaPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def of[A](request: OreRequest[A], user: User)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[UserData] = {
    for {
      isOrga <- user.isOrganization
      projectCount <- user.projects.size
      orga <- user.toMaybeOrganization
      viewActivity <- user can ViewActivity in user map ((ViewActivity, _))
      reviewFlags <- user can ReviewFlags in user map ((ReviewFlags, _))
      reviewProjects <- user can ReviewProjects in user map ((ReviewProjects, _))
      editSettings <- user can EditSettings in orga map ((EditSettings, _))
    } yield {

      val userPerms: Map[Permission, Boolean] = Seq(viewActivity, reviewFlags, reviewProjects).toMap
      val orgaPerms: Map[Permission, Boolean] = Seq(editSettings).toMap

      UserData(request.data,
              user,
              isOrga,
              projectCount,
              Seq.empty,
              userPerms,
              orgaPerms)

    }

  }
}
