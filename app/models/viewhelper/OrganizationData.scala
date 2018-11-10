package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, ProjectTableMain}
import db.{DbRef, ModelService}
import models.project.Project
import models.user.role.{OrganizationUserRole, ProjectUserRole}
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.permission._
import ore.permission.role.RoleCategory
import util.syntax._

import cats.data.OptionT
import cats.instances.future._
import slick.lifted.TableQuery

case class OrganizationData(
    joinable: Organization,
    members: Seq[(OrganizationUserRole, User)], // TODO sorted/reverse
    projectRoles: Seq[(ProjectUserRole, Project)]
) extends JoinableData[OrganizationUserRole, OrganizationMember, Organization] {

  def orga: Organization = joinable

  def roleCategory: RoleCategory = RoleCategory.Organization
}

object OrganizationData {
  val noPerms: Map[Permission, Boolean] = Map(EditSettings -> false)

  def cacheKey(orga: Organization): String = "organization" + orga.id.value

  def of[A](orga: Organization)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[OrganizationData] =
    for {
      members      <- orga.memberships.members(orga)
      memberRoles  <- Future.traverse(members)(_.headRole)
      memberUser   <- Future.traverse(memberRoles)(_.user)
      projectRoles <- service.runDBIO(queryProjectRoles(orga.id.value).result)
    } yield {
      val members = memberRoles.zip(memberUser)
      OrganizationData(orga, members.toSeq, projectRoles)
    }

  private def queryProjectRoles(userId: DbRef[User]) =
    for {
      (role, project) <- TableQuery[ProjectRoleTable].join(TableQuery[ProjectTableMain]).on(_.projectId === _.id)
      if role.userId === userId
    } yield (role, project)

  def of[A](orga: Option[Organization])(
      implicit ec: ExecutionContext,
      service: ModelService
  ): OptionT[Future, OrganizationData] = OptionT.fromOption[Future](orga).semiflatMap(of)
}
