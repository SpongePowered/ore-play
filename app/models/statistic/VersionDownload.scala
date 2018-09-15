package models.statistic

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._

import controllers.sugar.Requests.ProjectRequest
import db.impl.VersionDownloadsTable
import db.impl.access.UserBase
import models.project.Version
import ore.StatTracker._
import cats.instances.future._
import scala.concurrent.{ExecutionContext, Future}

import db.{ObjectId, ObjectReference, ObjectTimestamp}
import security.spauth.SpongeAuthApi

/**
  * Represents a unique download on a Project Version.
  *
  * @param id         Unique ID of entry
  * @param createdAt  Timestamp instant of creation
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class VersionDownload(id: ObjectId = ObjectId.Uninitialized,
                           createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                           modelId: ObjectReference,
                           address: InetString,
                           cookie: String,
                           userId: Option[ObjectReference] = None)
                           extends StatEntry[Version] {

  override type M = VersionDownload
  override type T = VersionDownloadsTable

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): VersionDownload = this.copy(id = id, createdAt = theTime)
}

object VersionDownload {

  /**
    * Creates a new VersionDownload to be (or not be) recorded from an incoming
    * request.
    *
    * @param version  Version downloaded
    * @param request  Request to bind
    * @return         New VersionDownload
    */
  def bindFromRequest(version: Version)(implicit ec: ExecutionContext, request: ProjectRequest[_], users: UserBase, auth: SpongeAuthApi): Future[VersionDownload] = {
    checkNotNull(version, "null version", "")
    checkArgument(version.isDefined, "undefined version", "")
    checkNotNull(request, "null request", "")
    checkNotNull(users, "null user base", "")
    users.current.map(_.id.value).value.map { userId =>
      VersionDownload(
        modelId = version.id.value,
        address = InetString(remoteAddress),
        cookie = currentCookie,
        userId = userId
      )
    }

  }

}
