package models.viewhelper

import play.api.mvc.Call

import controllers.routes
import controllers.sugar.Requests.OreRequest
import db.{Model, ModelService}
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationRoleTable, OrganizationTable, UserTable}
import models.project.Project
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
    user: Model[User],
    isOrga: Boolean,
    projectCount: Int,
    orgas: Seq[(Model[Organization], Model[User], Model[OrganizationUserRole], Model[User])],
    globalRoles: Set[Role],
    userPerm: Permission,
    orgaPerm: Permission
) {

  def global: HeaderData = headerData

  def hasUser: Boolean                 = global.hasUser
  def currentUser: Option[Model[User]] = global.currentUser

  def isCurrent: Boolean = currentUser.contains(user)

  def pgpFormCall: Call =
    user.pgpPubKey
      .as(routes.Users.verify(Some(routes.Users.deletePgpPublicKey(user.name, None, None).path)))
      .getOrElse(routes.Users.savePgpPublicKey(user.name))

  def pgpFormClass: String = user.pgpPubKey.as("pgp-delete").getOrElse("")

}

object UserData {

  private def queryRoles(user: Model[User]) =
    for {
      role    <- TableQuery[OrganizationRoleTable] if role.userId === user.id.value
      org     <- TableQuery[OrganizationTable] if role.organizationId === org.id
      orgUser <- TableQuery[UserTable] if org.id === orgUser.id
      owner   <- TableQuery[UserTable] if org.userId === owner.id
    } yield (org, orgUser, role, owner)

  def of[A](request: OreRequest[A], user: Model[User])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[UserData] =
    for {
      isOrga       <- user.toMaybeOrganization(ModelView.now(Organization)).isDefined
      projectCount <- user.projects(ModelView.now(Project)).size
      t            <- perms(user)
      (globalRoles, userPerms, orgaPerms) = t
      orgas <- service.runDBIO(queryRoles(user).result)
    } yield UserData(request.headerData, user, isOrga, projectCount, orgas, globalRoles, userPerms, orgaPerms)

  def perms(user: Model[User])(
      implicit cs: ContextShift[IO],
      service: ModelService
  ): IO[(Set[Role], Permission, Permission)] = {
    (
      user.permissionsIn(GlobalScope),
      user.toMaybeOrganization(ModelView.now(Organization)).semiflatMap(user.permissionsIn(_)).value,
      user.globalRoles.allFromParent,
    ).parMapN { (userPerms, orgaPerms, globalRoles) =>
      (globalRoles.map(_.toRole).toSet, userPerms, orgaPerms.getOrElse(Permission.None))
    }
  }
}
