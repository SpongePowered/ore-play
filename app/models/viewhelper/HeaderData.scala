package models.viewhelper

import play.api.mvc.Request

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{FlagTable, NotificationTable, ProjectTableMain, SessionTable, UserTable, VersionTable}
import db.{DbRef, ModelService}
import models.project.{ReviewState, Visibility}
import models.user.User
import ore.permission._
import ore.permission.scope.GlobalScope

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import org.slf4j.MDC
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

  def isCurrentUser(userId: DbRef[User]): Boolean = currentUser.map(_.id.value).contains(userId)

  def globalPerm(perm: Permission): Boolean = globalPermissions.getOrElse(perm, false)
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
      implicit service: ModelService
  ): IO[HeaderData] =
    OptionT
      .fromOption[IO](request.cookies.get("_oretoken"))
      .flatMap(cookie => getSessionUser(cookie.value))
      .semiflatMap(getHeaderData)
      .getOrElse(unAuthenticated)

  private def getSessionUser(token: String)(implicit service: ModelService) = {
    val query = for {
      s <- TableQuery[SessionTable] if s.token === token
      u <- TableQuery[UserTable] if s.username === u.name
    } yield (s, u)

    OptionT(service.runDBIO(query.result.headOption)).collect {
      case (session, user) if !session.hasExpired => user
    }
  }

  private def projectApproval(user: User) =
    TableQuery[ProjectTableMain]
      .filter(p => p.userId === user.id.value && p.visibility === (Visibility.NeedsApproval: Visibility))
      .exists

  private def reviewQueue: Rep[Boolean] =
    TableQuery[VersionTable].filter(v => v.reviewStatus === (ReviewState.Unreviewed: ReviewState)).exists

  private val flagQueue: Rep[Boolean] = TableQuery[FlagTable].filter(_.isResolved === false).exists

  private def getHeaderData(
      user: User
  )(implicit service: ModelService) = {

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

      service.runDBIO(query.result.head).map {
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

  def perms(user: User)(implicit service: ModelService): IO[Map[Permission, Boolean]] =
    user
      .trustIn(GlobalScope)
      .map2(user.globalRoles.allFromParent(user))((t, r) => user.can.asMap(t, r.toSet)(globalPerms: _*))
}
