package models.viewhelper

import akka.stream.actor.ActorPublisherMessage.Request
import controllers.sugar.Requests.OreRequest
import db.impl.{ProjectMembersTable, ProjectRoleTable, UserTable}
import models.admin.VisibilityChange
import models.project._
import models.user.User
import models.user.role.{OrganizationRole, ProjectRole}
import ore.organization.OrganizationMember
import ore.permission._
import ore.project.ProjectMember
import play.api.cache.AsyncCacheApi
import play.twirl.api.Html
import slick.jdbc.JdbcBackend
import db.impl.OrePostgresDriver.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:



case class ProjectData(headerData: HeaderData,
                       joinable: Project,
                       projectOwner: User,
                       canPostAsOwnerOrga: Boolean, // a.currentUser.get can PostAsOrganization in owner.toOrganization
                       ownerRole: ProjectRole,
                       versions: Int, // project.versions.size
                       settings: ProjectSettings,
                       permissions: Map[Permission, Boolean],
                       members: Seq[(ProjectRole, User)], // TODO sorted/reverse
                       uProjectFlags: Boolean, // TODO user.hasUnresolvedFlagFor(project)
                       starred: Boolean,
                       watching: Boolean,
                       projectLogSize: Int,
                       flags: Seq[(Flag, String, Option[String])], // (Flag, user.name, resolvedBy)
                       noteCount: Int, // getNotes.size
                       lastVisibilityChange: Option[VisibilityChange],
                       lastVisibilityChangeUser: String // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
                      ) extends JoinableData[ProjectRole, ProjectMember, Project]()
{

  def flagCount = flags.size

  def project: Project = joinable

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser

  def visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange = lastVisibilityChange.map(_.renderComment())
}

object ProjectData {

  def of[A](request: OreRequest[A], project: Project)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext): Future[ProjectData] = {

    implicit val userBase = project.userBase
    // TODO cache and fill
    for {
      settings <- project.settings
      projectOwner <- project.owner.user
      orgaOwner <- projectOwner.toMaybeOrganization
      canPostAsOwnerOrga <- request.data.currentUser.get can PostAsOrganization in orgaOwner // TODO none.get
      ownerRole <- project.owner.headRole
      versions <- project.versions.size
      perms <- perms(request.data.currentUser, project)
      members <- members(project)
      uProjectFlags <- request.data.currentUser.get.hasUnresolvedFlagFor(project) // TODO none.get
      starred <- project.stars.contains(request.data.currentUser.get) // TODO none.get
      watching <- project.watchers.contains(request.data.currentUser.get)  // TODO none.get
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

      new ProjectData(request.data, project, projectOwner,
        canPostAsOwnerOrga,
        ownerRole,
        versions,
        settings,
        perms,
        members,
        uProjectFlags,
        starred,
        watching,
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
      u <- tableUser if r.userId == u.id
    } yield {
      (r, u)
    }

    db.run(query.result).map(_.map {
      case (r, u) => (r, u)
    })
  }

  def perms(currentUser: Option[User], project: Project)(implicit ec: ExecutionContext): Future[Map[Permission, Boolean]] = {
    if (currentUser.isEmpty) Future.successful(noPerms)
    else {
      val user = currentUser.get
      for {
        editPages <- user can EditPages in project map ((EditPages, _))
        editSettings <- user can EditSettings in project map ((EditSettings, _))
        editChannels <- user can EditChannels in project map ((EditChannels, _))
        editVersions <- user can EditVersions in project map ((EditVersions, _))
        visibilities <- Future.sequence(VisibilityTypes.values.map(_.permission).map(p => user can p in project map ((p, _))))
      } yield {
        val perms = visibilities + editPages + editSettings + editChannels + editVersions
        perms.toMap
      }
    }
  }

  val noPerms = Map(EditPages -> false,
      EditSettings -> false,
      EditChannels -> false,
      EditVersions -> false) ++ VisibilityTypes.values.map(_.permission).map((_, false)).toMap

}