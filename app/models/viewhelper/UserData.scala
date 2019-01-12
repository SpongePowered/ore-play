package models.viewhelper

import play.api.mvc.Call

import controllers.routes
import controllers.sugar.Requests.OreRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationRoleTable, OrganizationTable, UserTable}
import models.user.role.OrganizationUserRole
import models.user.{Organization, User}
import ore.permission._
import ore.permission.role.Role
import ore.permission.scope.GlobalScope

import cats.effect.{ContextShift, IO}
import cats.instances.option._
import cats.syntax.all._
import slick.lifted.TableQuery

// TODO separate Scoped UserData

case class UserData(
    headerData: HeaderData,
    user: User,
    isOrga: Boolean,
    projectCount: Int,
    orgas: Seq[(Organization, User, OrganizationUserRole, User)],
    globalRoles: Set[Role],
    userPerm: Map[Permission, Boolean],
    orgaPerm: Map[Permission, Boolean]
) {

  def global: HeaderData = headerData

  def hasUser: Boolean          = global.hasUser
  def currentUser: Option[User] = global.currentUser

  def isCurrent: Boolean = currentUser.contains(user)

  def pgpFormCall: Call =
    user.pgpPubKey
      .as(routes.Users.verify(Some(routes.Users.deletePgpPublicKey(user.name, None, None).path)))
      .getOrElse(routes.Users.savePgpPublicKey(user.name))

  def pgpFormClass: String = user.pgpPubKey.as("pgp-delete").getOrElse("")

}

object UserData {

  private def queryRoles(user: User) =
    for {
      role    <- TableQuery[OrganizationRoleTable] if role.userId === user.id.value
      org     <- TableQuery[OrganizationTable] if role.organizationId === org.id
      orgUser <- TableQuery[UserTable] if org.id === orgUser.id
      owner   <- TableQuery[UserTable] if org.userId === owner.id
    } yield (org, orgUser, role, owner)

  def of[A](request: OreRequest[A], user: User)(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[UserData] =
    for {
      isOrga       <- user.toMaybeOrganization.isDefined
      projectCount <- user.projects.size
      t            <- perms(user)
      (globalRoles, userPerms, orgaPerms) = t
      orgas <- service.runDBIO(queryRoles(user).result)
    } yield UserData(request.headerData, user, isOrga, projectCount, orgas, globalRoles, userPerms, orgaPerms)

  def perms(user: User)(
      implicit cs: ContextShift[IO],
      service: ModelService
  ): IO[(Set[Role], Map[Permission, Boolean], Map[Permission, Boolean])] = {
    (
      user.trustIn(GlobalScope),
      user.toMaybeOrganization.semiflatMap(user.trustIn[Organization]).value,
      user.globalRoles.allFromParent(user),
    ).parMapN { (userTrust, orgTrust, globalRoles) =>
      val userPerms = user.can.asMap(userTrust, globalRoles.toSet)(ViewActivity, ReviewFlags, ReviewProjects)
      val orgaPerms = user.can.asMap(orgTrust, Some(globalRoles.toSet))(EditSettings)

      (globalRoles.map(_.toRole).toSet, userPerms, orgaPerms)
    }
  }
}
