package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import play.twirl.api.Html

import controllers.sugar.Requests.OreRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.schema.{ProjectRoleTable, UserTable}
import models.admin.ProjectVisibilityChange
import models.project._
import models.user.User
import models.user.role.ProjectRole
import ore.OreConfig
import ore.permission.role.Role
import ore.project.ProjectMember
import ore.project.factory.PendingProject

import cats.instances.future._
import cats.instances.option._
import cats.syntax.all._
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

/**
  * Holds ProjetData that is the same for all users
  */
case class ProjectData(
    joinable: Project,
    projectOwner: User,
    publicVersions: Int, // project.versions.count(_.visibility === VisibilityTypes.Public)
    settings: ProjectSettings,
    members: Seq[(ProjectRole, User)],
    projectLogSize: Int,
    flags: Seq[(Flag, String, Option[String])], // (Flag, user.name, resolvedBy)
    noteCount: Int, // getNotes.size
    lastVisibilityChange: Option[ProjectVisibilityChange],
    lastVisibilityChangeUser: String, // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
    recommendedVersion: Option[Version]
) extends JoinableData[ProjectRole, ProjectMember, Project] {

  def flagCount: Int = flags.size

  def project: Project = joinable

  def visibility: Visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange(implicit config: OreConfig): Option[Html] = lastVisibilityChange.map(_.renderComment)

  def roleClass: Class[_ <: Role] = classOf[ProjectRole]
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
      implicit db: JdbcBackend#DatabaseDef,
      ec: ExecutionContext,
      service: ModelService
  ): Future[ProjectData] = {
    val flagsFut     = project.flags.all
    val flagUsersFut = flagsFut.flatMap(flags => Future.traverse(flags)(_.user))
    val flagResolvedFut =
      flagsFut.flatMap(flags => Future.traverse(flags)(_.resolvedBy.flatTraverse(UserBase().get(_).value)))

    val lastVisibilityChangeFut = project.lastVisibilityChange.value
    val lastVisibilityChangeUserFut = lastVisibilityChangeFut.flatMap { lastVisibilityChange =>
      if (lastVisibilityChange.isEmpty) Future.successful("Unknown")
      else lastVisibilityChange.get.created.fold("Unknown")(_.name)
    }

    (
      project.settings,
      project.owner.user,
      project.versions.count(_.visibility === (Visibility.Public: Visibility)),
      members(project),
      project.logger.flatMap(_.entries.size),
      flagsFut,
      flagUsersFut,
      flagResolvedFut,
      lastVisibilityChangeFut,
      lastVisibilityChangeUserFut,
      project.recommendedVersion
    ).mapN {
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
          members.sortBy(_._1.roleType.trust).reverse,
          logSize,
          flagData.toSeq,
          noteCount,
          lastVisibilityChange,
          lastVisibilityChangeUser,
          Some(recommendedVersion)
        )
    }
  }

  def members(
      project: Project
  )(implicit db: JdbcBackend#DatabaseDef): Future[Seq[(ProjectRole, User)]] = {
    val query = for {
      r <- TableQuery[ProjectRoleTable] if r.projectId === project.id.value
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u)

    db.run(query.result)
  }
}
