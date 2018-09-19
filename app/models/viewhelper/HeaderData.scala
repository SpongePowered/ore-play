package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.Request

import controllers.sugar.Requests.ProjectRequest
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{FlagTable, NotificationTable, ProjectTableMain, SessionTable, UserTable, VersionTable}
import db.{ModelService, ObjectReference}
import models.project.Visibility
import models.user.User
import ore.permission._
import ore.permission.scope.GlobalScope

import cats.data.OptionT
import cats.instances.future._
import org.slf4j.MDC
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

/**
  * Holds global user specific data - When a User is not authenticated a dummy is used
  */
case class HeaderData(
    currentUser: Option[User] = None,
    private val globalPermissions: Map[Permission, Boolean] = Map.empty,
    hasNotice: Boolean = false,
    hasUnreadNotifications: Boolean = false,
    unresolvedFlags: Boolean = false,
    hasProjectApprovals: Boolean = false,
    hasReviewQueue: Boolean = false // queue.nonEmpty
) {

  // Just some helpers in templates:
  def isAuthenticated: Boolean = currentUser.isDefined

  def hasUser: Boolean = currentUser.isDefined

  def isCurrentUser(userId: ObjectReference): Boolean = currentUser.map(_.id.value).contains(userId)

  def globalPerm(perm: Permission): Boolean = globalPermissions.getOrElse(perm, false)

  def projectPerm(perm: Permission)(implicit r: ProjectRequest[_] = null): Boolean =
    if (r == null) false // Not a scoped request
    else r.scoped.permissions(perm)

}

object HeaderData {

  private val globalPerms = Seq(
    ReviewFlags,
    ReviewVisibility,
    ReviewProjects,
    ViewStats,
    ViewHealth,
    ViewLogs,
    HideProjects,
    HardRemoveProject,
    HardRemoveVersion,
    UserAdmin,
    HideProjects
  )

  val noPerms: Map[Permission, Boolean] = globalPerms.map(_ -> false).toMap

  val unAuthenticated: HeaderData = HeaderData(None, noPerms)

  def cacheKey(user: User) = s"""user${user.id.value}"""

  def of[A](request: Request[A])(
      implicit db: JdbcBackend#DatabaseDef,
      ec: ExecutionContext,
      service: ModelService
  ): Future[HeaderData] =
    OptionT
      .fromOption[Future](request.cookies.get("_oretoken"))
      .flatMap(cookie => getSessionUser(cookie.value))
      .semiflatMap(user => getHeaderData(user))
      .getOrElse(unAuthenticated)

  private def getSessionUser(token: String)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) = {
    val tableSession = TableQuery[SessionTable]
    val tableUser    = TableQuery[UserTable]

    val query = for {
      s <- tableSession if s.token === token
      u <- tableUser if s.username === u.name
    } yield {
      (s, u)
    }

    OptionT(db.run(query.result.headOption)).collect {
      case (session, user) if !session.hasExpired => user
    }
  }

  private def projectApproval(user: User) =
    TableQuery[ProjectTableMain]
      .filter(p => p.userId === user.id.value && p.visibility === (Visibility.NeedsApproval: Visibility))
      .exists

  private def reviewQueue: Rep[Boolean] =
    TableQuery[VersionTable].filter(v => v.isReviewed === false).exists

  private val flagQueue: Rep[Boolean] = TableQuery[FlagTable].filter(_.isResolved === false).exists

  private def getHeaderData(
      user: User
  )(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef, service: ModelService) = {

    MDC.put("currentUserId", user.id.toString)
    MDC.put("currentUserName", user.name)

    perms(user).flatMap { perms =>
      val query = Query.apply(
        (
          TableQuery[NotificationTable].filter(n => n.userId === user.id.value && !n.read).exists,
          if (perms(ReviewFlags)) flagQueue else false.bind,
          if (perms(ReviewVisibility)) projectApproval(user) else false.bind,
          if (perms(ReviewProjects)) reviewQueue else false.bind
        )
      )

      db.run(query.result.head).map {
        case (unreadNotif, unresolvedFlags, hasProjectApprovals, hasReviewQueue) =>
          HeaderData(
            Some(user),
            perms,
            unreadNotif || unresolvedFlags || hasProjectApprovals || hasReviewQueue,
            unreadNotif,
            unresolvedFlags,
            hasProjectApprovals,
            hasReviewQueue
          )
      }
    }
  }

  def perms(user: User)(implicit ec: ExecutionContext, service: ModelService): Future[Map[Permission, Boolean]] =
    user
      .trustIn(GlobalScope)
      .map(trust => globalPerms.map(perm => perm -> user.can(perm).withTrust(trust)).toMap)
}
