package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError}
import db.impl.query.APIV2Queries
import ore.db.impl.OrePostgresDriver.api._
import models.protocols.APIV2
import ore.db.DbRef
import ore.db.impl.schema.PageTable
import ore.models.project.{Page, Project}
import ore.permission.Permission
import ore.util.StringUtils
import util.syntax._

import slick.lifted.TableQuery
import zio.ZIO

class Pages(val errorHandler: HttpErrorHandler, lifecycle: ApplicationLifecycle)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {

  def showPages(pluginId: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF {
      service.runDbCon(APIV2Queries.pageList(pluginId).to[Vector]).flatMap { pages =>
        if (pages.isEmpty) ZIO.fail(NotFound)
        else ZIO.succeed(Ok(APIV2.PageList(pages.map(t => APIV2.PageListEntry(t._3, t._4)))))
      }
    }

  def showPage(pluginId: String, page: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF {
      service.runDbCon(APIV2Queries.getPage(pluginId, page).option).get.asError(NotFound).map {
        case (_, _, name, contents) =>
          Ok(APIV2.Page(name, contents))
      }
    }

  def putPage(pluginId: String, page: String): Action[APIV2.Page] =
    ApiAction(Permission.EditPage, APIScope.ProjectScope(pluginId)).asyncF(parseCirce.decodeJson[APIV2.Page]) { c =>
      val newName = StringUtils.compact(c.body.name)
      val content = c.body.content

      val pageArr  = page.split("/")
      val pageInit = pageArr.init.mkString("/")
      val slug     = StringUtils.slugify(pageArr.last) //TODO: Check ASCII

      val updateExisting = service.runDbCon(APIV2Queries.getPage(pluginId, page).option).get.flatMap {
        case (_, id, _, _) =>
          service
            .runDBIO(
              TableQuery[PageTable].filter(_.id === id).map(p => (p.name, p.contents)).update((newName, content))
            )
            .as(Ok(APIV2.Page(newName, content)))
      }

      def insertNewPage(projectId: DbRef[Project], parentId: Option[DbRef[Page]]) =
        service
          .insert(Page(projectId, parentId, newName, slug, isDeletable = true, content))
          .as(Created(APIV2.Page(newName, content)))

      val createNew =
        if (page.contains("/")) {
          service.runDbCon(APIV2Queries.getPage(pluginId, pageInit).option).get.asError(NotFound).flatMap {
            case (projectId, parentId, _, _) =>
              insertNewPage(projectId, Some(parentId))
          }
        } else {
          projects.withPluginId(pluginId).get.asError(NotFound).map(_.id).flatMap(insertNewPage(_, None))
        }

      if (page == Page.homeName && content.length < Page.minLength) ZIO.fail(BadRequest(ApiError("Too short content")))
      else if (content.length > Page.maxLengthPage) ZIO.fail(BadRequest(ApiError("Too long content")))
      else updateExisting.orElse(createNew)
    }

  def deletePage(pluginId: String, page: String): Action[AnyContent] =
    ApiAction(Permission.EditPage, APIScope.ProjectScope(pluginId)).asyncF {
      service
        .runDbCon(APIV2Queries.getPage(pluginId, page).option)
        .get
        .asError(NotFound)
        .flatMap {
          case (_, id, _, _) =>
            service.deleteWhere(Page)(p => p.id === id && p.isDeletable)
        }
        .map {
          case 0 => BadRequest(ApiError("Page not deletable"))
          case _ => NoContent
        }
    }
}
