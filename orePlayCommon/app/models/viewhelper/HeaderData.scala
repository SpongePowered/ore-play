package models.viewhelper

import scala.language.higherKinds

import play.api.mvc.Request

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema._
import ore.models.project.{ReviewState, Visibility}
import ore.models.user.User
import ore.db.{DbRef, Model, ModelService}
import ore.permission._
import ore.permission.scope.GlobalScope

import cats.{Functor, Monad}
import cats.data.OptionT
import cats.syntax.all._
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

  def of[F[_]](request: Request[_])(
      implicit service: ModelService[F],
      F: Monad[F]
  ): F[HeaderData] =
    OptionT
      .fromOption[F](request.cookies.get("_oretoken"))
      .flatMap(cookie => getSessionUser(cookie.value))
      .semiflatMap(getHeaderData[F])
      .getOrElse(unAuthenticated)

  private def getSessionUser[F[_]](token: String)(implicit service: ModelService[F], F: Functor[F]) = {
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

  private def getHeaderData[F[_]](
      user: Model[User]
  )(implicit service: ModelService[F], F: Monad[F]) = {
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
