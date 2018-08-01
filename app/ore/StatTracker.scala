package ore

import java.util.UUID

import controllers.sugar.Bakery
import controllers.sugar.Requests.{OreRequest, ProjectRequest}
import db.ModelService
import db.impl.schema.StatSchema
import javax.inject.Inject

import models.project.Version
import models.statistic.{ProjectView, VersionDownload}
import ore.StatTracker.COOKIE_NAME
import play.api.cache.AsyncCacheApi
import play.api.mvc.{RequestHeader, Result}
import util.instances.future._
import util.syntax._
import scala.concurrent.{ExecutionContext, Future}

import security.spauth.SpongeAuthApi

/**
  * Helper class for handling tracking of statistics.
  */
trait StatTracker {

  implicit def service: ModelService

  val bakery: Bakery
  val viewSchema: StatSchema[ProjectView]
  val downloadSchema: StatSchema[VersionDownload]

  /**
    * Signifies that a project has been viewed with the specified request and
    * actions should be taken to check whether a view should be added to the
    * Project's view count.
    *
    * @param request Request to view the project
    */
  def projectViewed(projectRequest: ProjectRequest[_])(f: ProjectRequest[_] => Result)(implicit cache: AsyncCacheApi, request: OreRequest[_],
      ec: ExecutionContext, auth: SpongeAuthApi): Future[Result] = {
    ProjectView.bindFromRequest(projectRequest).flatMap { statEntry =>
      this.viewSchema.record(statEntry).flatMap {
        case true  => projectRequest.data.project.addView
        case false => Future.unit
      }.as(f(projectRequest).withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
    }
  }

  /**
    * Signifies that a version has been downloaded with the specified request
    * and actions should be taken to check whether a view should be added to
    * the Version's (and Project's) download count.
    *
    * @param version Version to check downloads for
    * @param request Request to download the version
    */
  def versionDownloaded(version: Version)(f: ProjectRequest[_] => Result)(implicit cache: AsyncCacheApi,request: ProjectRequest[_],
      ec: ExecutionContext, auth: SpongeAuthApi): Future[Result] = {
    VersionDownload.bindFromRequest(version).flatMap { statEntry =>
      this.downloadSchema.record(statEntry).flatMap {
        case true  => version.addDownload *> request.data.project.addDownload
        case false => Future.unit
      }.as(f(request).withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
    }
  }

}

object StatTracker {

  val COOKIE_NAME = "_stat"

  /**
    * Gets or creates a unique ID for tracking statistics based on the browser.
    *
    * @param request  Request with cookie
    * @return         New or existing cookie
    */
  def currentCookie(implicit request: RequestHeader): String
  = request.cookies.get(COOKIE_NAME).map(_.value).getOrElse(UUID.randomUUID.toString)

  /**
    * Returns either the original client address from a X-Forwarded-For header
    * or the remoteAddress from the request if the header is not found.
    *
    * @param request  Request to get address of
    * @return         Remote address
    */
  def remoteAddress(implicit request: RequestHeader): String = {
    request.headers.get("X-Forwarded-For") match {
      case None => request.remoteAddress
      case Some(header) => header.split(',').headOption.map(_.trim).getOrElse(request.remoteAddress)
    }
  }

}

class OreStatTracker @Inject()(val service: ModelService, override val bakery: Bakery) extends StatTracker {
  override val viewSchema    : StatSchema[ProjectView]     = this.service.getSchemaByModel(classOf[ProjectView]).asInstanceOf[StatSchema[ProjectView]]

  override val downloadSchema: StatSchema[VersionDownload] = this.service.getSchemaByModel(classOf[VersionDownload])
    .asInstanceOf[StatSchema[VersionDownload]]
}
