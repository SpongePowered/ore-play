package ore

import java.util.UUID
import javax.inject.Inject

import play.api.mvc.{RequestHeader, Result}

import controllers.sugar.Bakery
import controllers.sugar.Requests.ProjectRequest
import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.{DbRef, Model, ModelFilter, ModelQuery, ModelService}
import models.project.{Project, Version}
import models.statistic.{ProjectView, StatEntry, VersionDownload}
import models.user.User
import ore.StatTracker.COOKIE_NAME
import security.spauth.SpongeAuthApi

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Helper class for handling tracking of statistics.
  */
trait StatTracker {

  implicit def service: ModelService

  def bakery: Bakery

  private def record[S <: Model, M0 <: StatEntry[S] { type M = M0 }: ModelQuery](
      entry: M0
  )(setUserId: (M0, DbRef[User]) => M0): IO[Boolean] = {
    like[S, M0](entry).value.flatMap {
      case None => service.insert(entry).as(true)
      case Some(existingEntry) =>
        val effect = if (existingEntry.userId.isEmpty && entry.userId.isDefined) {
          service.update(setUserId(existingEntry, entry.userId.get)).void
        } else IO.unit
        effect.as(false)
    }
  }

  private def like[S <: Model, M <: StatEntry[S]: ModelQuery](
      entry: M
  ): OptionT[IO, M] = {
    val baseFilter = ModelFilter[M](_.modelId === entry.modelId)
    val filter     = ModelFilter[M](e => e.address === entry.address || e.cookie === entry.cookie)

    val userFilter = entry.user.map(u => ModelFilter[M](e => filter(e) || e.userId === u.id.value)).getOrElse(filter)
    OptionT.liftF(userFilter).flatMap(uFilter => service.find(baseFilter && uFilter))
  }

  /**
    * Signifies that a project has been viewed with the specified request and
    * actions should be taken to check whether a view should be added to the
    * Project's view count.
    */
  def projectViewed(f: => Result)(
      implicit projectRequest: ProjectRequest[_],
      auth: SpongeAuthApi
  ): IO[Result] = {
    ProjectView.bindFromRequest.flatMap { statEntry =>
      record[Project, ProjectView](statEntry)((m, id) => m.copy(userId = Some(id)))
        .flatMap {
          case true  => projectRequest.data.project.addView
          case false => IO.unit
        }
        .as(f.withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
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
  def versionDownloaded(version: Version)(f: IO[Result])(
      implicit request: ProjectRequest[_],
      auth: SpongeAuthApi,
      cs: ContextShift[IO]
  ): IO[Result] = {
    VersionDownload.bindFromRequest(version).flatMap { statEntry =>
      val recordDownload = record[Version, VersionDownload](statEntry)((m, id) => m.copy(userId = Some(id))).flatMap {
        case true  => version.addDownload *> request.data.project.addDownload
        case false => IO.unit
      }

      recordDownload &> f.map(_.withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
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

}

class OreStatTracker @Inject()(val service: ModelService, override val bakery: Bakery) extends StatTracker
