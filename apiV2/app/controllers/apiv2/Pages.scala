package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors}
import controllers.sugar.Requests.ApiRequest
import controllers.sugar.ResolvedAPIScope
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.PageTable
import ore.models.project.{Page, Project, Webhook}
import ore.permission.Permission
import ore.util.StringUtils
import util.PatchDecoder
import util.syntax._

import cats.data.Validated
import cats.instances.option._
import cats.syntax.all._
import io.circe.{Decoder, Json}
import slick.lifted.TableQuery
import squeal.category._
import squeal.category.syntax.all._
import squeal.category.macros.Derive
import zio.ZIO
import zio.interop.catz._

class Pages(val errorHandler: HttpErrorHandler, lifecycle: ApplicationLifecycle)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {

  def showPages(projectOwner: String, projectSlug: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF { request =>
      service.runDbCon(APIV2Queries.pageList(request.scope.id).to[Vector]).flatMap { pages =>
        if (pages.isEmpty) ZIO.fail(NotFound)
        else ZIO.succeed(Ok(APIV2.PageList(pages.map(t => APIV2.PageListEntry(t._2, t._3, t._4)))))
      }
    }

  private def getPageOpt(
      page: String
  )(
      implicit request: ApiRequest[ResolvedAPIScope.ProjectScope, _]
  ): ZIO[Any, Option[Nothing], (DbRef[Page], String, Option[String])] =
    service
      .runDbCon(APIV2Queries.getPage(request.scope.id, page).option)
      .get

  private def getPage(
      page: String
  )(
      implicit request: ApiRequest[ResolvedAPIScope.ProjectScope, _]
  ): ZIO[Any, Status, (DbRef[Page], String, Option[String])] =
    getPage(page).orElseFail(NotFound)

  def showPageAction(projectOwner: String, projectSlug: String, page: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF { request =>
      service.runDbCon(APIV2Queries.getPage(request.scope.id, page).option).get.orElseFail(NotFound).map {
        case (_, name, contents) =>
          Ok(APIV2.Page(name, contents))
      }
    }

  def putPage(projectOwner: String, projectSlug: String, page: String): Action[APIV2.Page] =
    ApiAction(Permission.EditPage, APIScope.ProjectScope(projectOwner, projectSlug))
      .asyncF(parseCirce.decodeJson[APIV2.Page]) { implicit r =>
        val newName = StringUtils.compact(r.body.name)
        val content = r.body.content

        val pageArr    = page.split("/").toIndexedSeq
        val pageParent = pageArr.init.mkString("/")
        val slug       = StringUtils.slugify(pageArr.last) //TODO: Check ASCII

        val updateExisting = getPageOpt(page).flatMap {
          case (id, _, _) =>
            addWebhookJob(
              Webhook.WebhookEventType.PageUpdated,
              APIV2.PageUpdateWithSlug(newName, pageArr, pageArr, content.isDefined, content),
              ???
            ) *> service
              .runDBIO(
                TableQuery[PageTable].filter(_.id === id).map(p => (p.name, p.contents)).update((newName, content))
              )
              .as(Ok(APIV2.Page(newName, content)))
        }

        def insertNewPage(parentId: Option[DbRef[Page]]) = {
          addWebhookJob(
            Webhook.WebhookEventType.PageCreated,
            APIV2.PageWithSlug(newName, pageArr, content.isDefined, content),
            ???
          ) *> service
            .insert(Page(r.scope.id, parentId, newName, slug, isDeletable = true, content))
            .as(Created(APIV2.Page(newName, content)))
        }

        val createNew =
          if (page.contains("/")) {
            getPage(pageParent).flatMap {
              case (parentId, _, _) =>
                insertNewPage(Some(parentId))
            }
          } else {
            insertNewPage(None)
          }

        if (page == Page.homeName && content.fold(0)(_.length) < Page.minLength)
          ZIO.fail(BadRequest(ApiError("Too short content")))
        else if (content.fold(0)(_.length) > Page.maxLengthPage) ZIO.fail(BadRequest(ApiError("Too long content")))
        else updateExisting.orElse(createNew)
      }

  def patchPage(projectOwner: String, projectSlug: String, page: String): Action[Json] =
    ApiAction(Permission.EditPage, APIScope.ProjectScope(projectOwner, projectSlug))
      .asyncF(parseCirce.json) { implicit request =>
        val root = request.body.hcursor

        val res: Decoder.AccumulatingResult[Pages.PatchPageF[Option]] = Pages.PatchPageF.patchDecoder.traverseKC(
          λ[PatchDecoder ~>: Compose2[Decoder.AccumulatingResult, Option, *]](_.decode(root))
        )

        res match {
          case Validated.Valid(a) =>
            val edits = a.copy[Option](
              name = a.name.map(StringUtils.compact)
            )

            val slug = edits.name.map(StringUtils.slugify)

            val oldPage = getPage(page)
            val newParent =
              edits.parent
                .map(_.map(p => getPageOpt(p).map(_._1)).sequence)
                .sequence
                .orElseFail(BadRequest(ApiError("Unknown parent")))

            val runRename = (oldPage <&> newParent).flatMap {
              case ((id, name, contents), parentId) =>
                val oldSlug   = page.split("/").toIndexedSeq
                val oldParent = oldSlug.dropRight(1)
                val newParent = edits.parent.map(_.fold(IndexedSeq.empty[String])(_.split("/").toIndexedSeq))

                addWebhookJob(
                  Webhook.WebhookEventType.PageUpdated,
                  APIV2.PageUpdateWithSlug(
                    edits.name.getOrElse(name),
                    oldSlug,
                    newParent.getOrElse(oldParent.toList) :+ slug.getOrElse(oldSlug.last),
                    edits.content.getOrElse(contents).isDefined,
                    edits.content.getOrElse(contents)
                  ),
                  ???
                ) *>
                  service
                    .runDbCon(APIV2Queries.patchPage(edits, slug, id, parentId).run)
                    .as(Ok(APIV2.Page(edits.name.getOrElse(name), edits.content.getOrElse(contents))))
            }

            if (edits.content.flatten.fold(0)(_.length) > Page.maxLengthPage)
              ZIO.fail(BadRequest(ApiError("Too long content")))
            else runRename
          case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e.map(_.show))))
        }
      }

  def deletePage(projectOwner: String, projectSlug: String, page: String): Action[AnyContent] =
    ApiAction(Permission.EditPage, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF { implicit request =>
      getPage(page)
        .flatMap {
          case (id, _, _) =>
            //TODO: In the future when we represent the tree in a better way, just promote all children one level up
            service.deleteWhere(Page)(p =>
              p.id === id && p.isDeletable && TableQuery[PageTable].filter(_.parentId === id).size === 0
            )
        }
        .flatMap {
          case 0 => ZIO.succeed(BadRequest(ApiError("Page not deletable")))
          case _ =>
            addWebhookJob(Webhook.WebhookEventType.PageUpdated, APIV2.PageSlug(page.split("/").toIndexedSeq), ???)
              .as(NoContent)
        }
    }
}
object Pages {
  case class PatchPageF[F[_]](
      name: F[String],
      content: F[Option[String]],
      parent: F[Option[String]]
  )
  object PatchPageF {
    implicit val F: ApplicativeKC[PatchPageF] with TraverseKC[PatchPageF] with DistributiveKC[PatchPageF] =
      Derive.allKC[PatchPageF]

    val patchDecoder: PatchPageF[PatchDecoder] =
      PatchDecoder.fromName(Derive.namesWithProductImplicitsC[PatchPageF, Decoder])(
        io.circe.derivation.renaming.snakeCase
      )
  }
}
