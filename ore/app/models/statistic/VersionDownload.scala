package models.statistic

import controllers.sugar.Requests.ProjectRequest
import db.impl.access.UserBase
import db.impl.schema.VersionDownloadsTable
import db.{Model, DbRef, DefaultModelCompanion, ModelQuery}
import models.project.Version
import models.user.User
import ore.StatTracker._
import security.spauth.SpongeAuthApi

import cats.effect.IO
import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents a unique download on a Project Version.
  *
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class VersionDownload(
    modelId: DbRef[Version],
    address: InetString,
    cookie: String,
    userId: Option[DbRef[User]]
) extends StatEntry[Version]
object VersionDownload
    extends DefaultModelCompanion[VersionDownload, VersionDownloadsTable](TableQuery[VersionDownloadsTable]) {

  implicit val query: ModelQuery[VersionDownload] = ModelQuery.from(this)

  /**
    * Creates a new VersionDownload to be (or not be) recorded from an incoming
    * request.
    *
    * @param version  Version downloaded
    * @param request  Request to bind
    * @return         New VersionDownload
    */
  def bindFromRequest(version: Model[Version])(
      implicit request: ProjectRequest[_],
      users: UserBase,
      auth: SpongeAuthApi
  ): IO[VersionDownload] = {
    users.current.map(_.id.value).value.map { userId =>
      VersionDownload(
        modelId = version.id,
        address = InetString(remoteAddress),
        cookie = currentCookie,
        userId = userId
      )
    }

  }

}
