package ore

import java.util.UUID

import play.api.mvc.{RequestHeader, Result}

import controllers.sugar.Bakery
import controllers.sugar.Requests.ProjectRequest
import _root_.db.impl.access.UserBase
import _root_.db.impl.query.StatTrackerQueries
import ore.db._
import ore.models.project.Version
import ore.models.user.User

import cats.{Monad, Parallel}
import cats.syntax.all._
import cats.tagless.autoInvariantK
import com.github.tminglei.slickpg.InetString

/**
  * Helper class for handling tracking of statistics.
  */
@autoInvariantK
trait StatTracker[F[_]] {

  /**
    * Signifies that a project has been viewed with the specified request and
    * actions should be taken to check whether a view should be added to the
    * Project's view count.
    */
  def projectViewed(result: F[Result])(implicit request: ProjectRequest[_]): F[Result]

  /**
    * Signifies that a version has been downloaded with the specified request
    * and actions should be taken to check whether a view should be added to
    * the Version's (and Project's) download count.
    *
    * @param version Version to check downloads for
    * @param request Request to download the version
    */
  def versionDownloaded(version: Model[Version])(result: F[Result])(implicit request: ProjectRequest[_]): F[Result]
}

object StatTracker {

  val COOKIE_NAME = "_stat"

  /**
    * Gets or creates a unique ID for tracking statistics based on the browser.
    *
    * @param request  Request with cookie
    * @return         New or existing cookie
    */
  def currentCookie(implicit request: RequestHeader): String =
    request.cookies.get(COOKIE_NAME).map(_.value).getOrElse(UUID.randomUUID.toString)

  /**
    * Returns either the original client address from a X-Forwarded-For header
    * or the remoteAddress from the request if the header is not found.
    *
    * @param request  Request to get address of
    * @return         Remote address
    */
  def remoteAddress(implicit request: RequestHeader): String =
    request.headers.get("X-Forwarded-For") match {
      case None         => request.remoteAddress
      case Some(header) => header.split(',').headOption.map(_.trim).getOrElse(request.remoteAddress)
    }

  /**
    * Helper class for handling tracking of statistics.
    */
  class StatTrackerInstant[F[_]](bakery: Bakery)(
      implicit service: ModelService[F],
      F: Monad[F]
  ) extends StatTracker[F] {

    private def applyUpdate(
        f: (InetString, String, Option[DbRef[User]]) => doobie.Query0[String]
    )(implicit projectRequest: ProjectRequest[_]) = {
      val userId = projectRequest.headerData.currentUser.map(_.id.value)
      f(InetString(StatTracker.remoteAddress), StatTracker.currentCookie, userId)
    }

    private def addStat(
        updateFunc: (InetString, String, Option[DbRef[User]]) => doobie.Query0[String],
        result: F[Result]
    )(implicit projectRequest: ProjectRequest[_]) =
      for {
        cookie    <- service.runDbCon(applyUpdate(updateFunc).unique)
        newResult <- result.map(_.withCookies(bakery.bake(COOKIE_NAME, cookie, secure = true)))
      } yield newResult

    def projectViewed(result: F[Result])(implicit projectRequest: ProjectRequest[_]): F[Result] =
      addStat(
        StatTrackerQueries.addProjectView(projectRequest.data.project.id, _, _, _),
        result
      )

    def versionDownloaded(
        version: Model[Version]
    )(result: F[Result])(implicit request: ProjectRequest[_]): F[Result] =
      addStat(
        StatTrackerQueries.addVersionDownload(version.id, version.projectId, _, _, _),
        result
      )

  }
}
