package models.viewhelper

import db.ModelService
import db.impl.{SessionTable, UserTable}
import models.user.User
import ore.permission._
import play.api.cache.AsyncCacheApi
import play.api.mvc.Request
import slick.lifted.TableQuery
import db.impl.OrePostgresDriver.OreDriver._
import db.impl.access.OrganizationBase
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in GlobalScope ;  ReviewFlags - ReviewVisibility - ReviewProjects - ViewStats -
//                              ViewHealth - ViewLogs - HideProjects - HardRemoveProject - UserAdmin
//                              HideProjects


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

  val unAuthenticated: HeaderData =
    HeaderData(None,
               Map(ReviewFlags -> false,
                   ReviewVisibility -> false,
                   ReviewProjects -> false,
                   ViewStats -> false,
                   ViewHealth -> false,
                   ViewLogs -> false,
                   HideProjects -> false,
                   HardRemoveProject -> false,
                   UserAdmin -> false,
                   HideProjects -> false))

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

  private def getHeaderData(user: User) = {

    Future.successful(
      HeaderData(Some(user),
        Map(ReviewFlags -> true,
          ReviewVisibility -> true,
          ReviewProjects -> true,
          ViewStats -> true,
          ViewHealth -> true,
          ViewLogs -> true,
          HideProjects -> true,
          HardRemoveProject -> true,
          UserAdmin -> true,
          HideProjects -> true)
      ))
    // TODO cache and fill
  }

}
