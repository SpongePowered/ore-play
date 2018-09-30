package models.statistic

import scala.concurrent.{ExecutionContext, Future}

import controllers.sugar.Requests.ProjectRequest
import db.impl.access.UserBase
import db.impl.schema.ProjectViewsTable
import db.{ObjectId, ObjectReference, ObjectTimestamp}
import models.project.Project
import ore.StatTracker._
import ore.permission.scope.ProjectScope
import security.spauth.SpongeAuthApi

import cats.instances.future._
import com.github.tminglei.slickpg.InetString

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
case class ProjectView(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    modelId: ObjectReference,
    address: InetString,
    cookie: String,
    userId: Option[ObjectReference] = None
) extends StatEntry[Project]
    with ProjectScope {

  override type M = ProjectView
  override type T = ProjectViewsTable

  override def projectId: ObjectReference                                    = this.modelId
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectView = this.copy(id = id, createdAt = theTime)
}

object ProjectView {

  /**
    * Creates a new ProjectView to be (or not be) recorded from an incoming
    * request.
    *
    * @param request  Request to bind
    * @return         New ProjectView
    */
  def bindFromRequest(
      implicit ec: ExecutionContext,
      users: UserBase,
      auth: SpongeAuthApi,
      request: ProjectRequest[_]
  ): Future[ProjectView] = {
    users.current.map(_.id.value).value.map { userId =>
      ProjectView(
        modelId = request.data.project.id.value,
        address = InetString(remoteAddress),
        cookie = currentCookie,
        userId = userId
      )

    }
  }

}
