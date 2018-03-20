package models.statistic

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
import controllers.sugar.Requests.ProjectRequest
import db.impl.ProjectViewsTable
import db.impl.access.UserBase
import models.project.Project
import ore.StatTracker._
import ore.permission.scope.ProjectScope

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a unique view on a Project.
  *
  * @param id         Unique ID of entry
  * @param createdAt  Timestamp instant of creation
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class ProjectView(override val id: Option[Int] = None,
                       override val createdAt: Option[Timestamp] = None,
                       override val modelId: Int,
                       override val address: InetString,
                       override val cookie: String,
                       private var _userId: Option[Int] = None)
                       extends StatEntry[Project](id, createdAt, modelId, address, cookie, _userId)
                         with ProjectScope {

  override type M = ProjectView
  override type T = ProjectViewsTable

  override def projectId: Int = this.modelId
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)

}

object ProjectView {

  /**
    * Creates a new ProjectView to be (or not be) recorded from an incoming
    * request.
    *
    * @param request  Request to bind
    * @return         New ProjectView
    */
  def bindFromRequest(request: ProjectRequest[_])(implicit ec: ExecutionContext, users: UserBase): Future[ProjectView] = {
    implicit val r = request.request
    checkNotNull(request, "null request", "")
    checkNotNull(users, "null user base", "")
    users.current.map { _.flatMap(_.id) } map { userId =>
      val view = ProjectView(
        modelId = request.data.project.id.get,
        address = InetString(remoteAddress),
        cookie = currentCookie,
        _userId = userId
      )
      view.userBase = users
      view
    }
  }

}
