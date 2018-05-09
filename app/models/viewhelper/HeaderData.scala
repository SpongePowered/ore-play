package models.viewhelper

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.access.OrganizationBase
import db.impl.{ProjectTableMain, SessionTable, UserTable, VersionTable}
import models.project.ProjectStates
import models.user.User
import ore.permission._
import ore.permission.scope.GlobalScope
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

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
                      hasReviewQueue: Boolean = false // versionApprovalQueue.nonEmpty
                     ) {

  // Just some helpers in templates:
  def isAuthenticated = currentUser.isDefined

  def hasUser = currentUser.isDefined

  def isCurrentUser(userId: Int) = currentUser.flatMap(_.id).contains(userId)

  def globalPerm(perm: Permission): Boolean = globalPermissions.getOrElse(perm, false)

  def projectPerm(perm: Permission)(implicit r: ProjectRequest[_] = null): Boolean = {
    if (r == null) false // Not a scoped request
    else r.scoped.permissions(perm)
  }

}

object HeaderData {

  val noPerms: Map[Permission, Boolean] = Map(ReviewFlags -> false,
                  ReviewVersions -> false,
                  ReviewProjects -> false,
                  ViewStats -> false,
                  ViewHealth -> false,
                  ViewLogs -> false,
                  HideProjects -> false,
                  HardRemoveProject -> false,
                  UserAdmin -> false,
                  HideProjects -> false)

  val unAuthenticated: HeaderData = HeaderData(None, noPerms)

  def cacheKey(user: User) = s"""user${user.id.get}"""

  def of[A](request: Request[A])(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[HeaderData] = {
    request.cookies.get("_oretoken") match {
      case None => Future.successful(unAuthenticated)
      case Some(cookie) =>
        getSessionUser(cookie.value).flatMap {
          case None => Future.successful(unAuthenticated)
          case Some(user) =>
            user.service = service
            user.organizationBase = service.getModelBase(classOf[OrganizationBase])
            getHeaderData(user)
        }
    }
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

    db.run(query.result.headOption).map {
      case None => None
      case Some((session, user)) =>
        if (session.hasExpired) None else Some(user)
    }
  }

  private def projectApproval(user: User)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef): Future[Boolean] = {

    val tableProject = TableQuery[ProjectTableMain]
    val query = for {
      p <- tableProject if p.userId === user.id.get && p.state === ProjectStates.NeedsApproval
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

  private def getHeaderData(user: User)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) = {

    for {
      perms <- perms(Some(user))
      hasNotice <- user.hasNotice
      unreadNotif <- user.notifications.filterNot(_.read).map(_.nonEmpty)
      unresolvedFlags <- user.flags.filterNot(_.isResolved).map(_.nonEmpty)
      hasProjectApprovals <- projectApproval(user)
      hasReviewQueue <- if (perms(ReviewProjects)) reviewQueue() else Future.successful(false)
    } yield {
        HeaderData(Some(user),
        perms,
        hasNotice,
        unreadNotif,
        unresolvedFlags,
        hasProjectApprovals,
        hasReviewQueue)
    }
  }


  def perms(currentUser: Option[User])(implicit ec: ExecutionContext): Future[Map[Permission, Boolean]] = {
    if (currentUser.isEmpty) Future.successful(noPerms)
    else {
      val user = currentUser.get
      for {
        reviewFlags       <- user can ReviewFlags in GlobalScope map ((ReviewFlags, _))
        reviewVersions  <- user can ReviewVersions in GlobalScope map ((ReviewVersions, _))
        reviewProjects    <- user can ReviewProjects in GlobalScope map ((ReviewProjects, _))
        viewStats         <- user can ViewStats in GlobalScope map ((ViewStats, _))
        viewHealth        <- user can ViewHealth in GlobalScope map ((ViewHealth, _))
        viewLogs          <- user can ViewLogs in GlobalScope map ((ViewLogs, _))
        hideProjects      <- user can HideProjects in GlobalScope map ((HideProjects, _))
        hardRemoveProject <- user can HardRemoveProject in GlobalScope map ((HardRemoveProject, _))
        userAdmin         <- user can UserAdmin in GlobalScope map ((UserAdmin, _))
        hideProjects      <- user can HideProjects in GlobalScope map ((HideProjects, _))
      } yield {
        val perms = Seq(reviewFlags, reviewVersions, reviewProjects, viewStats, viewHealth, viewLogs, hideProjects, hardRemoveProject, userAdmin, hideProjects)
        perms toMap
      }
    }
  }

}
