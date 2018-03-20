package models.viewhelper

import controllers.sugar.Requests.OreRequest
import db.impl.OrePostgresDriver.api._
import db.impl.{ProjectRoleTable, UserTable}
import models.admin.VisibilityChange
import models.project._
import models.user.User
import models.user.role.ProjectRole
import ore.project.ProjectMember
import ore.project.factory.PendingProject
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

/**
  * Holds ProjetData that is the same for all users
  */
case class ProjectData(joinable: Project,
                       projectOwner: User,
                       ownerRole: ProjectRole,
                       versions: Int, // project.versions.size
                       settings: ProjectSettings,
                       members: Seq[(ProjectRole, User)],
                       projectLogSize: Int,
                       flags: Seq[(Flag, String, Option[String])], // (Flag, user.name, resolvedBy)
                       noteCount: Int, // getNotes.size
                       lastVisibilityChange: Option[VisibilityChange],
                       lastVisibilityChangeUser: String // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
                      ) extends JoinableData[ProjectRole, ProjectMember, Project] {

  def flagCount = flags.size

  def project: Project = joinable

  def visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange = lastVisibilityChange.map(_.renderComment())
}

object ProjectData {

  def cacheKey(project: Project) = "project" + project.id.get

  def of[A](request: OreRequest[A], project: PendingProject)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[ProjectData] = {

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
      lastVisibilityChangeUser)

    Future.successful(data)
  }
  def of[A](project: Project)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[ProjectData] = {

    implicit val userBase = project.userBase
    for {
      settings <- project.settings
      projectOwner <- project.owner.user

      ownerRole <- project.owner.headRole
      versions <- project.versions.size
      members <- members(project)

      logSize <- project.logger.flatMap(_.entries.size)
      flags <- project.flags.all
      flagUsers <- Future.sequence(flags.map(_.user))
      flagResolved <- Future.sequence(flags.map(flag => flag.userBase.get(flag.resolvedBy.getOrElse(-1))))
      lastVisibilityChange <- project.lastVisibilityChange
      lastVisibilityChangeUser <- if (lastVisibilityChange.isEmpty) Future.successful("Unknown")
      else lastVisibilityChange.get.created.map(_.map(_.name).getOrElse("Unknown"))
    } yield {
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
        lastVisibilityChangeUser)
    }
  }

  def members(project: Project)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef): Future[Seq[(ProjectRole, User)]] = {
    val tableUser = TableQuery[UserTable]
    val tableRole = TableQuery[ProjectRoleTable]

    val query = for {
      r <- tableRole if r.projectId === project.id.get
      u <- tableUser if r.userId === u.id
    } yield {
      (r, u)
    }

    db.run(query.result).map(_.map {
      case (r, u) => (r, u)
    })
  }



}