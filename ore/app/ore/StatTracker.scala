package ore

import scala.language.higherKinds

import java.util.UUID

import play.api.mvc.{RequestHeader, Result}

import controllers.sugar.Bakery
import controllers.sugar.Requests.ProjectRequest
import _root_.db.impl.access.UserBase
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.StatTable
import ore.db.{ModelCompanion, _}
import ore.models.project.Version
import ore.models.statistic.{ProjectView, StatEntry, VersionDownload}
import ore.models.user.User
import ore.util.OreMDC
import _root_.util.TaskUtils

import cats.Parallel
import cats.data.OptionT
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.tagless.autoInvariantK
import com.github.tminglei.slickpg.InetString
import com.typesafe.scalalogging

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
  class StatTrackerInstant[F[_], G[_]](bakery: Bakery)(
      implicit service: ModelService[F],
      users: UserBase[F],
      F: cats.effect.Effect[F],
      par: Parallel[F, G]
  ) extends StatTracker[F] {

    private val Logger    = scalalogging.Logger("StatTracker")
    private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

    private def recordDB[M <: StatEntry[_, M], T <: StatTable[_, M]](
        entry: M,
        model: ModelCompanion.Aux[M, T]
    ): F[Boolean] = {
      like(entry, model).value.flatMap {
        case None => service.insertRaw(model)(entry).as(true)
        case Some(existingEntry) =>
          val effect =
            if (existingEntry.userId.isEmpty && entry.userId.isDefined)
              service.updateRaw(model)(existingEntry)(_.withUserId(Some(entry.userId.get))).void
            else
              F.unit

          effect.as(false)
      }
    }

    private def like[M <: StatEntry[_, M], T <: StatTable[_, M]](
        entry: M,
        model: ModelCompanion.Aux[M, T]
    ): OptionT[F, Model[M]] = {
      val baseFilter: T => Rep[Boolean] = _.modelId === entry.modelId
      val filter: T => Rep[Boolean]     = _.cookie === entry.cookie

      val userFilter = entry.userId.fold(filter)(id => e => filter(e) || e.userId === id)
      ModelView.now(model).find(baseFilter && userFilter)
    }

    private def createEntry[A](
        f: (InetString, String, Option[DbRef[User]]) => A
    )(implicit projectRequest: ProjectRequest[_]) =
      users.current
        .map(_.id.value)
        .value
        .map(userId => f(InetString(StatTracker.remoteAddress), StatTracker.currentCookie, userId))

    private def addStat[M <: StatEntry[_, M], T <: StatTable[_, M]](
        createEntryFunc: (InetString, String, Option[DbRef[User]]) => M,
        entryCompanion: ModelCompanion.Aux[M, T],
        describe: String,
        addStatNow: F[Unit],
        result: F[Result]
    )(implicit projectRequest: ProjectRequest[_]) = {
      createEntry(createEntryFunc).flatMap { statEntry =>
        val stat = recordDB(statEntry, entryCompanion).flatMap {
          case true  => addStatNow
          case false => F.unit
        }

        val doUpdateAsync =
          stat.runAsync(TaskUtils.logCallback(s"Failed to register $describe", MDCLogger)).to[F]
        val setCookie = result.map(_.withCookies(bakery.bake(COOKIE_NAME, statEntry.cookie, secure = true)))

        doUpdateAsync *> setCookie
      }
    }

    def projectViewed(result: F[Result])(implicit projectRequest: ProjectRequest[_]): F[Result] = {
      addStat(
        ProjectView(projectRequest.data.project.id, _, _, _),
        ProjectView,
        "project view",
        projectRequest.data.project.addView.void,
        result
      )
    }

    def versionDownloaded(
        version: Model[Version]
    )(result: F[Result])(implicit request: ProjectRequest[_]): F[Result] = {
      addStat(
        VersionDownload(version.id, _, _, _),
        VersionDownload,
        "version download",
        version.addDownload &> request.data.project.addDownload.void,
        result
      )
    }

  }
}
