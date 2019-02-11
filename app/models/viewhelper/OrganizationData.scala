package models.viewhelper

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, ProjectTableMain}
import db.{DbModel, DbRef, ModelService}
import models.project.Project
import models.user.role.{OrganizationUserRole, ProjectUserRole}
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.permission._
import ore.permission.role.RoleCategory
import util.syntax._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

case class OrganizationData(
    joinable: DbModel[Organization],
    members: Seq[(DbModel[OrganizationUserRole], DbModel[User])], // TODO sorted/reverse
    projectRoles: Seq[(DbModel[ProjectUserRole], DbModel[Project])]
) extends JoinableData[OrganizationUserRole, OrganizationMember, Organization] {

  def orga: DbModel[Organization] = joinable

  def roleCategory: RoleCategory = RoleCategory.Organization
}

object OrganizationData {
  val noPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def cacheKey(orga: DbModel[Organization]): String = "organization" + orga.id

  def of[A](orga: DbModel[Organization])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[OrganizationData] = {
    import cats.instances.vector._
    for {
      members      <- orga.memberships.members(orga)
      memberRoles  <- members.toVector.parTraverse(_.headRole)
      memberUser   <- memberRoles.parTraverse(_.user)
      projectRoles <- service.runDBIO(queryProjectRoles(orga.id).result)
    } yield {
      val members = memberRoles.zip(memberUser)
      OrganizationData(orga, members, projectRoles)
    }
  }

  private def queryProjectRoles(userId: DbRef[User]) =
    for {
      (role, project) <- TableQuery[ProjectRoleTable].join(TableQuery[ProjectTableMain]).on(_.projectId === _.id)
      if role.userId === userId
    } yield (role, project)

  def of[A](orga: Option[DbModel[Organization]])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): OptionT[IO, OrganizationData] = OptionT.fromOption[IO](orga).semiflatMap(of)
}
