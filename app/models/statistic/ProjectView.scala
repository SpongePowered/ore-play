package models.statistic

import controllers.sugar.Requests.ProjectRequest
import db.impl.access.UserBase
import db.impl.schema.ProjectViewsTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}
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
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class ProjectView(
    modelId: DbRef[Project],
    address: InetString,
    cookie: String,
    userId: Option[DbRef[User]]
) extends StatEntry[Project]

object ProjectView extends DefaultModelCompanion[ProjectView, ProjectViewsTable](TableQuery[ProjectViewsTable]) {

  implicit val query: ModelQuery[ProjectView] = ModelQuery.from(this)

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
        modelId = request.data.project.id,
        address = InetString(remoteAddress),
        cookie = currentCookie,
        userId = userId
      )

    }
  }

}
