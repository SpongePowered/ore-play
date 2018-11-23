package models.statistic

import controllers.sugar.Requests.ProjectRequest
import db.impl.access.UserBase
import db.impl.schema.ProjectViewsTable
import db.{DbRef, ModelQuery, ObjId, ObjectTimestamp}
import models.project.Project
import models.user.User
import ore.StatTracker._
import security.spauth.SpongeAuthApi

import cats.effect.IO
import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

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
    id: ObjId[ProjectView] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    modelId: DbRef[Project],
    address: InetString,
    cookie: String,
    userId: Option[DbRef[User]] = None
) extends StatEntry[Project] {

  override type M = ProjectView
  override type T = ProjectViewsTable
}

object ProjectView {

  implicit val query: ModelQuery[ProjectView] =
    ModelQuery.from[ProjectView](TableQuery[ProjectViewsTable], _.copy(_, _))

  /**
    * Creates a new ProjectView to be (or not be) recorded from an incoming
    * request.
    *
    * @param request  Request to bind
    * @return         New ProjectView
    */
  def bindFromRequest(
      implicit users: UserBase,
      auth: SpongeAuthApi,
      request: ProjectRequest[_]
  ): IO[ProjectView] = {
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
