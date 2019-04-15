package models.viewhelper

import play.api.mvc.Request

import db.impl.OrePostgresDriver.api._
import db.impl.schema._
import models.project.{ReviewState, Visibility}
import models.user.User
import ore.db.{DbRef, Model, ModelService}
import ore.permission._
import ore.permission.scope.GlobalScope

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import slick.lifted.TableQuery

/**
  * Holds global user specific data - When a User is not authenticated a dummy is used
  */
case class HeaderData(
    currentUser: Option[Model[User]] = None,
    globalPermissions: Permission = Permission.None,
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

  def globalPerm(perm: Permission): Boolean = globalPermissions.has(perm)
}

object HeaderData {

  val unAuthenticated: HeaderData = HeaderData()

  def cacheKey(user: Model[User]) = s"""user${user.id}"""

  def of[A](request: Request[A])(
      implicit service: ModelService,
      cs: ContextShift[IO]
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

  private def projectApproval(user: Model[User]) =
    TableQuery[ProjectTableMain]
      .filter(p => p.userId === user.id.value && p.visibility === (Visibility.NeedsApproval: Visibility))
      .exists

  private def reviewQueue: Rep[Boolean] =
    TableQuery[VersionTable].filter(v => v.reviewStatus === (ReviewState.Unreviewed: ReviewState)).exists

  private val flagQueue: Rep[Boolean] = TableQuery[FlagTable].filter(_.isResolved === false).exists

  private def getHeaderData(
      user: Model[User]
  )(implicit service: ModelService) = {
    user.permissionsIn(GlobalScope).flatMap { perms =>
      val query = Query.apply(
        (
          TableQuery[NotificationTable].filter(n => n.userId === user.id.value && !n.read).exists,
          if (perms.has(Permission.ModNotesAndFlags)) flagQueue else false.bind,
          if (perms.has(Permission.ModNotesAndFlags ++ Permission.SeeHidden)) projectApproval(user)
          else false.bind,
          if (perms.has(Permission.Reviewer)) reviewQueue else false.bind
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
}
