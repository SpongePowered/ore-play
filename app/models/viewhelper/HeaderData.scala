package models.viewhelper

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.OrganizationBase
import db.impl._
import models.project.VisibilityTypes
import models.user.User
import ore.permission._
import ore.permission.scope.GlobalScope
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery
import util.functional.OptionT
import util.instances.future._
import util.syntax._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Holds global user specific data - When a User is not authenticated a dummy is used
  */
case class HeaderData(currentUser: Option[User] = None,
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

  def isCurrentUser(userId: Int): Boolean = currentUser.map(_.id.value).contains(userId)

  def globalPerm(perm: Permission): Boolean = globalPermissions.getOrElse(perm, false)

  def projectPerm(perm: Permission)(implicit r: ProjectRequest[_] = null): Boolean = {
    if (r == null) false // Not a scoped request
    else r.scoped.permissions(perm)
  }

}

object HeaderData {

  val noPerms: Map[Permission, Boolean] = Map(ReviewFlags -> false,
                  ReviewVisibility -> false,
                  ReviewProjects -> false,
                  ViewStats -> false,
                  ViewHealth -> false,
                  ViewLogs -> false,
                  HideProjects -> false,
                  HardRemoveProject -> false,
                  HardRemoveVersion -> false,
                  UserAdmin -> false,
                  HideProjects -> false)

  val unAuthenticated: HeaderData = HeaderData(None, noPerms)

  def cacheKey(user: User) = s"""user${user.id.value}"""

  def of[A](request: Request[A])(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[HeaderData] = {
    OptionT.fromOption[Future](request.cookies.get("_oretoken"))
      .flatMap(cookie => getSessionUser(cookie.value))
      .semiFlatMap { user =>
        user.service = service
        user.organizationBase = service.getModelBase(classOf[OrganizationBase])
        getHeaderData(user)
      }
      .getOrElse(unAuthenticated)
  }

  private def getSessionUser(token: String)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) = {
    val tableSession = TableQuery[SessionTable]
    val tableUser = TableQuery[UserTable]

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

  private def projectApproval(user: User)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef): Future[Boolean] = {

    val tableProject = TableQuery[ProjectTableMain]
    val query = for {
      p <- tableProject if p.userId === user.id.value && p.visibility === VisibilityTypes.NeedsApproval
    } yield {
      p
    }
    db.run(query.exists.result)
  }

  private def reviewQueue()(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) : Future[Boolean] = {
    val tableVersion = TableQuery[VersionTable]

    val query = for {
      v <- tableVersion if v.isReviewed === false
    } yield {
      v
    }

    db.run(query.exists.result)

  }

  private def flagQueue()(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) : Future[Boolean] = {
    val tableFlags = TableQuery[FlagTable]

    val query = for {
      v <- tableFlags if v.isResolved === false
    } yield {
      v
    }

    db.run(query.exists.result)
  }

  private def getHeaderData(user: User)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) = {

    perms(Some(user)).flatMap { perms =>
      (
        user.hasNotice,
        user.notifications.filterNot(_.read).map(_.nonEmpty),
        flagQueue(),
        projectApproval(user),
        if (perms(ReviewProjects)) reviewQueue() else Future.successful(false)
      ).parMapN { (hasNotice, unreadNotif, unresolvedFlags, hasProjectApprovals, hasReviewQueue) =>
        HeaderData(Some(user),
          perms,
          hasNotice,
          unreadNotif,
          unresolvedFlags,
          hasProjectApprovals,
          hasReviewQueue)
      }
    }
  }


  def perms(currentUser: Option[User])(implicit ec: ExecutionContext): Future[Map[Permission, Boolean]] = {
    if (currentUser.isEmpty) Future.successful(noPerms)
    else {
      val user = currentUser.get
      (
        user can ReviewFlags in GlobalScope map ((ReviewFlags, _)),
        user can ReviewVisibility in GlobalScope map ((ReviewVisibility, _)),
        user can ReviewProjects in GlobalScope map ((ReviewProjects, _)),
        user can ViewStats in GlobalScope map ((ViewStats, _)),
        user can ViewHealth in GlobalScope map ((ViewHealth, _)),
        user can ViewLogs in GlobalScope map ((ViewLogs, _)),
        user can HideProjects in GlobalScope map ((HideProjects, _)),
        user can HardRemoveProject in GlobalScope map ((HardRemoveProject, _)),
        user can HardRemoveVersion in GlobalScope map ((HardRemoveProject, _)),
        user can UserAdmin in GlobalScope map ((UserAdmin, _)),
      ).parMapN {
        case (reviewFlags, reviewVisibility, reviewProjects, viewStats, viewHealth, viewLogs, hideProjects, hardRemoveProject, hardRemoveVersion, userAdmin) =>
          val perms = Seq(reviewFlags, reviewVisibility, reviewProjects, viewStats, viewHealth, viewLogs, hideProjects, hardRemoveProject, hardRemoveVersion, userAdmin)
          perms.toMap
      }
    }
  }

}
