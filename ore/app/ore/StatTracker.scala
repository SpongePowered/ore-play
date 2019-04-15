package ore

import java.util.UUID
import javax.inject.Inject

import play.api.mvc.{RequestHeader, Result}

import controllers.sugar.Bakery
import controllers.sugar.Requests.ProjectRequest
import _root_.db.impl.OrePostgresDriver.api._
import _root_.db.impl.table.StatTable
import models.project.{Project, Version}
import models.statistic.{ProjectView, StatEntry, VersionDownload}
import models.user.User
import ore.StatTracker.COOKIE_NAME
import ore.db.access.ModelView
import ore.db._
import security.spauth.SpongeAuthApi
import util.{IOUtils, OreMDC}

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.typesafe.scalalogging

/**
  * Helper class for handling tracking of statistics.
  */
trait StatTracker {

  implicit def service: ModelService

  def bakery: Bakery

  private val Logger    = scalalogging.Logger("StatTracker")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  private def record[S, M <: StatEntry[S]: ModelQuery, T <: StatTable[S, M]](
      entry: M,
      subject: ModelCompanion[S],
      model: ModelCompanion.Aux[M, T]
  )(setUserId: (M, DbRef[User]) => M): IO[Boolean] = {
    like[S, M, T](entry, model).value.flatMap {
      case None => service.insert(entry).as(true)
      case Some(existingEntry) =>
        val effect =
          if (existingEntry.userId.isEmpty && entry.userId.isDefined)
            service.update(existingEntry)(setUserId(_, entry.userId.get)).void
          else
            IO.unit
        effect.as(false)
    }
  }

  private def like[S, M <: StatEntry[S]: ModelQuery, T <: StatTable[S, M]](
      entry: M,
      model: ModelCompanion.Aux[M, T]
  ): OptionT[IO, Model[M]] = {
    val baseFilter: T => Rep[Boolean] = _.modelId === entry.modelId
    val filter: T => Rep[Boolean]     = _.cookie === entry.cookie

    val userFilter = entry.userId.fold(filter)(id => e => filter(e) || e.userId === id)
    ModelView.now(model).find(baseFilter && userFilter)
  }

  /**
    * Signifies that a project has been viewed with the specified request and
    * actions should be taken to check whether a view should be added to the
    * Project's view count.
    */
  def projectViewed(f: => Result)(
      implicit projectRequest: ProjectRequest[_],
      auth: SpongeAuthApi,
      mdc: OreMDC
  ): IO[Result] = {
    ProjectView.bindFromRequest.flatMap { statEntry =>
      val projectView =
        record(statEntry, Project, ProjectView)((m, id) => m.copy(userId = Some(id)))
          .flatMap {
            case true  => projectRequest.data.project.addView
            case false => IO.unit
          }

      projectView
        .runAsync(IOUtils.logCallback("Failed to register project view", MDCLogger))
        .as(f.withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
        .toIO
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
  def versionDownloaded(version: Model[Version])(f: IO[Result])(
      implicit request: ProjectRequest[_],
      auth: SpongeAuthApi,
      mdc: OreMDC,
      cs: ContextShift[IO]
  ): IO[Result] = {
    VersionDownload.bindFromRequest(version).flatMap { statEntry =>
      val recordDownload =
        record(statEntry, Version, VersionDownload)((m, id) => m.copy(userId = Some(id)))
          .flatMap {
            case true  => version.addDownload &> request.data.project.addDownload
            case false => IO.unit
          }

      recordDownload.runAsync(IOUtils.logCallback("Failed to register version download", MDCLogger)).toIO *> f
        .map(_.withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))
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
