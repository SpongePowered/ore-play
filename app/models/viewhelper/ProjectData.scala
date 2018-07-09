package models.viewhelper

import controllers.sugar.Requests.OreRequest
import db.impl.OrePostgresDriver.api._
import db.impl.{ProjectRoleTable, UserTable}
import models.admin.ProjectVisibilityChange
import models.project._
import models.user.User
import models.user.role.ProjectRole
import ore.project.ProjectMember
import ore.project.factory.PendingProject
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import play.twirl.api.Html
import util.syntax._
import util.instances.future._

/**
  * Holds ProjetData that is the same for all users
  */
case class ProjectData(joinable: Project,
                       projectOwner: User,
                       ownerRole: ProjectRole,
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

  def visibility: VisibilityTypes.Visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange: Option[Html] = lastVisibilityChange.map(_.renderComment())
}

object ProjectData {

  def cacheKey(project: Project): String = "project" + project.id.value

  def of[A](request: OreRequest[A], project: PendingProject)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): ProjectData = {

    val projectOwner = request.data.currentUser.get

    val settings = project.settings
    val ownerRole = null
    val versions = 0
    val members = Seq.empty
    val uProjectFlags = false
    val starred = false
    val watching = false
    val logSize = 0
    val lastVisibilityChange = None
    val lastVisibilityChangeUser = "-"
    val recommendedVersion = None

    val data = new ProjectData(project.underlying,
      projectOwner,
      ownerRole,
      versions,
      settings,
      members,
      logSize,
      Seq.empty,
      0,
      lastVisibilityChange,
      lastVisibilityChangeUser,
      recommendedVersion)

    data
  }

  def of[A](project: Project)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[ProjectData] = {

    implicit val userBase: UserBase = project.userBase

    val flagsFut = project.flags.all
    val flagUsersFut = flagsFut.flatMap(flags => Future.sequence(flags.map(_.user)))
    val flagResolvedFut = flagsFut.flatMap(flags => Future.sequence(flags.map(flag => flag.userBase.get(flag.resolvedBy.getOrElse(-1)).value)))

    val lastVisibilityChangeFut = project.lastVisibilityChange.value
    val lastVisibilityChangeUserFut = lastVisibilityChangeFut.flatMap { lastVisibilityChange =>
      if (lastVisibilityChange.isEmpty) Future.successful("Unknown") else lastVisibilityChange.get.created.fold("Unknown")(_.name)
    }

    (
      project.settings,
      project.owner.user,
      project.owner.headRole,
      project.versions.count(_.visibility === VisibilityTypes.Public),
      members(project),
      project.logger.flatMap(_.entries.size),
      flagsFut,
      flagUsersFut,
      flagResolvedFut,
      lastVisibilityChangeFut,
      lastVisibilityChangeUserFut,
      project.recommendedVersion
    ).parMapN {
      case (settings, projectOwner, ownerRole, versions, members, logSize, flags, flagUsers, flagResolved, lastVisibilityChange, lastVisibilityChangeUser, recommendedVersion) =>
        val noteCount = project.getNotes().size
        val flagData = flags zip flagUsers zip flagResolved map { case ((fl, user), resolved) =>
          (fl, user.name, resolved.map(_.username))
        }

        new ProjectData(
          project,
          projectOwner,
          ownerRole,
          versions,
          settings,
          members.sortBy(_._1.roleType.trust).reverse,
          logSize,
          flagData.toSeq,
          noteCount,
          lastVisibilityChange,
          lastVisibilityChangeUser,
          Some(recommendedVersion))
    }
  }

  def members(project: Project)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef): Future[Seq[(ProjectRole, User)]] = {
    val tableUser = TableQuery[UserTable]
    val tableRole = TableQuery[ProjectRoleTable]

    val query = for {
      r <- tableRole if r.projectId === project.id.value
      u <- tableUser if r.userId === u.id
    } yield {
      (r, u)
    }

    db.run(query.result).map(_.map {
      case (r, u) => (r, u)
    })
  }



}
