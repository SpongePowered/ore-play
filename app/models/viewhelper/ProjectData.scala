package models.viewhelper

import play.twirl.api.Html

import controllers.sugar.Requests.OreRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.schema.{ProjectRoleTable, UserTable}
import models.admin.ProjectVisibilityChange
import models.project._
import models.user.User
import models.user.role.ProjectUserRole
import ore.OreConfig
import ore.permission.role.RoleCategory
import ore.project.ProjectMember
import ore.project.factory.PendingProject
import util.syntax._

import cats.effect.{ContextShift, IO}
import cats.instances.option._
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Holds ProjetData that is the same for all users
  */
case class ProjectData(
    joinable: Project,
    projectOwner: User,
    publicVersions: Int, // project.versions.count(_.visibility === VisibilityTypes.Public)
    settings: ProjectSettings,
    members: Seq[(ProjectUserRole, User)],
    projectLogSize: Int,
    flags: Seq[(Flag, String, Option[String])], // (Flag, user.name, resolvedBy)
    noteCount: Int, // getNotes.size
    lastVisibilityChange: Option[ProjectVisibilityChange],
    lastVisibilityChangeUser: String, // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
    recommendedVersion: Option[Version]
) extends JoinableData[ProjectUserRole, ProjectMember, Project] {

  def flagCount: Int = flags.size

  def project: Project = joinable

  def visibility: Visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange(implicit config: OreConfig): Option[Html] = lastVisibilityChange.map(_.renderComment)

  def roleCategory: RoleCategory = RoleCategory.Project
}

object ProjectData {

  def cacheKey(project: Project): String = "project" + project.id.value

  def of[A](
      request: OreRequest[A],
      project: PendingProject
  ): ProjectData = {

    val projectOwner = request.headerData.currentUser.get

    val settings                 = project.settings
    val versions                 = 0
    val members                  = Seq.empty
    val logSize                  = 0
    val lastVisibilityChange     = None
    val lastVisibilityChangeUser = "-"
    val recommendedVersion       = None

    new ProjectData(
      project.underlying,
      projectOwner,
      versions,
      settings,
      members,
      logSize,
      Seq.empty,
      0,
      lastVisibilityChange,
      lastVisibilityChangeUser,
      recommendedVersion
    )
  }

  def of[A](project: Project)(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[ProjectData] = {
    import cats.instances.vector._
    val flagsF        = project.flags.all.map(_.toVector)
    val flagUsersF    = flagsF.flatMap(flags => flags.parTraverse(_.user))
    val flagResolvedF = flagsF.flatMap(flags => flags.parTraverse(_.resolvedBy.flatTraverse(UserBase().get(_).value)))

    val lastVisibilityChangeF = project.lastVisibilityChange.value
    val lastVisibilityChangeUserF = lastVisibilityChangeF.flatMap { lastVisibilityChange =>
      lastVisibilityChange.fold(IO.pure("Unknown"))(_.created.fold("Unknown")(_.name))
    }

    (
      project.settings,
      project.owner.user,
      project.versions.count(_.visibility === (Visibility.Public: Visibility)),
      members(project),
      project.logger.flatMap(_.entries.size),
      flagsF,
      flagUsersF,
      flagResolvedF,
      lastVisibilityChangeF,
      lastVisibilityChangeUserF,
      project.recommendedVersion.value
    ).parMapN {
      case (
          settings,
          projectOwner,
          versions,
          members,
          logSize,
          flags,
          flagUsers,
          flagResolved,
          lastVisibilityChange,
          lastVisibilityChangeUser,
          recommendedVersion
          ) =>
        val noteCount = project.decodeNotes.size
        val flagData = flags.zip(flagUsers).zip(flagResolved).map {
          case ((fl, user), resolved) => (fl, user.name, resolved.map(_.name))
        }

        new ProjectData(
          project,
          projectOwner,
          versions,
          settings,
          members.sortBy(_._1.role.trust).reverse,
          logSize,
          flagData,
          noteCount,
          lastVisibilityChange,
          lastVisibilityChangeUser,
          recommendedVersion
        )
    }
  }

  def members(
      project: Project
  )(implicit service: ModelService): IO[Seq[(ProjectUserRole, User)]] = {
    val query = for {
      r <- TableQuery[ProjectRoleTable] if r.projectId === project.id.value
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u)

    service.runDBIO(query.result)
  }
}
