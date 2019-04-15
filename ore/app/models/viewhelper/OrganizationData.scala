package models.viewhelper

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, ProjectTableMain}
import models.project.Project
import models.user.role.{OrganizationUserRole, ProjectUserRole}
import models.user.{Organization, User}
import ore.db.{DbRef, Model, ModelService}
import ore.permission.role.RoleCategory
import util.syntax._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

case class OrganizationData(
    joinable: Model[Organization],
    members: Seq[(Model[OrganizationUserRole], Model[User])], // TODO sorted/reverse
    projectRoles: Seq[(Model[ProjectUserRole], Model[Project])]
) extends JoinableData[OrganizationUserRole, Organization] {

  def orga: Model[Organization] = joinable

  def roleCategory: RoleCategory = RoleCategory.Organization
}

object OrganizationData {

  def cacheKey(orga: Model[Organization]): String = "organization" + orga.id

  def of[A](orga: Model[Organization])(
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

  def of[A](orga: Option[Model[Organization]])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): OptionT[IO, OrganizationData] = OptionT.fromOption[IO](orga).semiflatMap(of)
}
