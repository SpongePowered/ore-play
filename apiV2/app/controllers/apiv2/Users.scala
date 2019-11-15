package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, Result}

import controllers.OreControllerComponents
import controllers.apiv2.Users.PaginatedCompactProjectResult
import controllers.apiv2.helpers.{APIScope, Pagination}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import ore.db.DbRef
import ore.models.project.ProjectSortingStrategy
import ore.models.user.User
import ore.permission.Permission

import cats.syntax.all._
import io.circe._
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.syntax._
import zio.interop.catz._
import zio.ZIO

class Users(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {

  def showUser(user: String): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF("showUser")(user) {
        service.runDbCon(APIV2Queries.userQuery(user).option).map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
      }
    }

  def showStarred(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction("showStarred")(
      user,
      sort,
      limit,
      offset,
      APIV2Queries.starredQuery,
      APIV2Queries.starredCountQuery
    )

  def showWatching(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction("showWatching")(
      user,
      sort,
      limit,
      offset,
      APIV2Queries.watchingQuery,
      APIV2Queries.watchingCountQuery
    )

  def showUserAction(cacheKey: String)(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long,
      query: (
          String,
          Boolean,
          Option[DbRef[User]],
          ProjectSortingStrategy,
          Long,
          Long
      ) => doobie.Query0[Either[DecodingFailure, APIV2.CompactProject]],
      countQuery: (String, Boolean, Option[DbRef[User]]) => doobie.Query0[Long]
  ): Action[AnyContent] = ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
    cachingF(cacheKey)(user, sort, limit, offset) {
      val realLimit = limitOrDefault(limit, config.ore.projects.initLoad)

      val getProjects = query(
        user,
        request.globalPermissions.has(Permission.SeeHidden),
        request.user.map(_.id),
        sort.getOrElse(ProjectSortingStrategy.Default),
        realLimit,
        offset
      ).to[Vector]

      val countProjects = countQuery(
        user,
        request.globalPermissions.has(Permission.SeeHidden),
        request.user.map(_.id)
      ).unique

      (service.runDbCon(getProjects).flatMap(ZIO.foreach(_)(ZIO.fromEither(_))).orDie, service.runDbCon(countProjects))
        .parMapN { (projects, count) =>
          Ok(
            PaginatedCompactProjectResult(
              Pagination(realLimit, offset, count),
              projects
            )
          )
        }
    }
  }
}
object Users {
  import APIV2.circeConfig

  @ConfiguredJsonCodec case class PaginatedCompactProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.CompactProject]
  )
}
