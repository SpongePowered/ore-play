package models.viewhelper

import db.ModelService
import db.impl.OrePostgresDriver.OreDriver._
import db.impl.access.OrganizationBase
import db.impl.{SessionTable, UserTable}
import models.user.User
import ore.permission._
import ore.permission.scope.GlobalScope
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.jdbc.JdbcBackend
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:


case class HeaderData(currentUser: Option[User] = None,
                      permissions: Map[Permission, Boolean] = Map.empty,

                      hasUnreadNotifications: Boolean = false,   // user.hasUnreadNotif
                      unresolvedFlags: Boolean = false,         // flags.filterNot(_.isResolved).nonEmpty
                      hasProjectApprovals: Boolean = false, // >= 1 val futureApproval = projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0)
                      hasReviewQueue: Boolean = false // queue.nonEmpty
                     ) {

  // Just some helpers in templates:
  def isAuthenticated = currentUser.isDefined

  def hasUser = currentUser.isDefined

  def isCurrentUser(userId: Int) = currentUser.map(_.id).contains(userId)

  def apply(permission: Permission): Boolean = permissions(permission)
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
                  UserAdmin -> false,
    HideProjects -> false)

  val unAuthenticated: HeaderData = HeaderData(None, noPerms)

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

  private def getHeaderData(user: User)(implicit ec: ExecutionContext, db: JdbcBackend#DatabaseDef) = {
    for {
      perms <- perms(Some(user))
    } yield {

      // TODO cache and fill

      HeaderData(Some(user), perms)
    }
  }


  def perms(currentUser: Option[User])(implicit ec: ExecutionContext): Future[Map[Permission, Boolean]] = {
    if (currentUser.isEmpty) Future.successful(noPerms)
    else {
      val user = currentUser.get
      for {
        reviewFlags       <- user can ReviewFlags in GlobalScope map ((ReviewFlags, _))
        reviewVisibility  <- user can ReviewVisibility in GlobalScope map ((ReviewVisibility, _))
        reviewProjects    <- user can ReviewProjects in GlobalScope map ((ReviewProjects, _))
        viewStats         <- user can ViewStats in GlobalScope map ((ViewStats, _))
        viewHealth        <- user can ViewHealth in GlobalScope map ((ViewHealth, _))
        viewLogs          <- user can ViewLogs in GlobalScope map ((ViewLogs, _))
        hideProjects      <- user can HideProjects in GlobalScope map ((HideProjects, _))
        hardRemoveProject <- user can HardRemoveProject in GlobalScope map ((HardRemoveProject, _))
        userAdmin         <- user can UserAdmin in GlobalScope map ((UserAdmin, _))
        hideProjects      <- user can HideProjects in GlobalScope map ((HideProjects, _))
      } yield {
        val perms = Seq(reviewFlags, reviewVisibility, reviewProjects, viewStats, viewHealth, viewLogs, hideProjects, hardRemoveProject, userAdmin, hideProjects)
        perms toMap
      }
    }
  }

}
