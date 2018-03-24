package models.viewhelper

import controllers.routes
import controllers.sugar.Requests.OreRequest
import db.ModelService
import db.impl.{OrganizationRoleTable, OrganizationTable, UserTable}
import models.user.role.OrganizationRole
import models.user.{Organization, User}
import ore.permission._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery
import db.impl.OrePostgresDriver.api._
import scala.concurrent.{ExecutionContext, Future}

// TODO separate Scoped UserData

case class UserData(headerData: HeaderData,
                    user: User,
                    isOrga: Boolean,
                    projectCount: Int,
                    orgas: Seq[(Organization, User, OrganizationRole, User)],
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

  private def queryRoles(user: User) = {
    val roleTable = TableQuery[OrganizationRoleTable]
    val orgaTable = TableQuery[OrganizationTable]
    val userTable = TableQuery[UserTable]

    for {
      r <- roleTable if r.userId === user.id.get
      o <- orgaTable if r.organizationId === o.id
      u <- userTable if o.id === u.id
      uo <- userTable if o.userId === uo.id
    } yield {
      (o, u, r, uo) // Organization OrgaUser Role Owner
    }

  }


  def of[A](request: OreRequest[A], user: User)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[UserData] = {
    for {
      isOrga <- user.isOrganization
      projectCount <- user.projects.size
      orga <- user.toMaybeOrganization
      viewActivity <- user can ViewActivity in user map ((ViewActivity, _))
      reviewFlags <- user can ReviewFlags in user map ((ReviewFlags, _))
      reviewProjects <- user can ReviewProjects in user map ((ReviewProjects, _))
      editSettings <- user can EditSettings in orga map ((EditSettings, _))
      orgas <- db.run(queryRoles(user).result)
    } yield {

      val userPerms: Map[Permission, Boolean] = Seq(viewActivity, reviewFlags, reviewProjects).toMap
      val orgaPerms: Map[Permission, Boolean] = Seq(editSettings).toMap

      UserData(request.data,
              user,
              isOrga,
              projectCount,
              orgas,
              userPerms,
              orgaPerms)

    }

  }
}
