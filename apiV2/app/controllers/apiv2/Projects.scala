package controllers.apiv2

import java.time.LocalDate
import java.time.format.DateTimeParseException

import play.api.http.HttpErrorHandler
import play.api.i18n.Lang
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, Result}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors, Pagination, UserError}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.APIV2ProjectStatsQuery
import ore.data.project.Category
import ore.models.project.{ProjectSortingStrategy, Version}
import ore.models.project.factory.{ProjectFactory, ProjectTemplate}
import ore.permission.Permission
import ore.util.OreMDC
import util.PatchDecoder
import util.syntax._

import cats.data.{NonEmptyList, Validated}
import cats.syntax.all._
import squeal.category._
import squeal.category.syntax.all._
import squeal.category.macros.Derive
import io.circe._
import io.circe.syntax._
import com.typesafe.scalalogging
import io.circe.derivation.annotations.SnakeCaseJsonCodec
import zio.interop.catz._
import zio.{UIO, ZIO}

class Projects(
    factory: ProjectFactory,
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Projects._

  private val Logger    = scalalogging.Logger("ApiV2Projects")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  def listProjects(
      q: Option[String],
      categories: Seq[Category],
      platforms: Seq[String],
      stability: Seq[Version.Stability],
      owner: Option[String],
      sort: Option[ProjectSortingStrategy],
      relevance: Option[Boolean],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
      val realLimit  = limitOrDefault(limit, config.ore.projects.initLoad)
      val realOffset = offsetOrZero(offset)

      val parsedPlatforms = platforms.map { s =>
        val splitted = s.split(":", 2)
        (splitted(0), splitted.lift(1))
      }

      val getProjects = APIV2Queries
        .projectQuery(
          None,
          categories.toList,
          parsedPlatforms.toList,
          stability.toList,
          q,
          owner,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id),
          sort.getOrElse(ProjectSortingStrategy.Default),
          relevance.getOrElse(true),
          realLimit,
          realOffset
        )
        .to[Vector]

      val countProjects = APIV2Queries
        .projectCountQuery(
          None,
          categories.toList,
          parsedPlatforms.toList,
          stability.toList,
          q,
          owner,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id)
        )
        .unique

      (
        service.runDbCon(getProjects).flatMap(ZIO.foreachParN(config.performance.nioBlockingFibers)(_)(identity)),
        service.runDbCon(countProjects)
      ).parMapN { (projects, count) =>
        Ok(
          PaginatedProjectResult(
            Pagination(realLimit, realOffset, count),
            projects
          )
        )
      }
    }

  //We check the perms ourselves later for this one
  def createProject(): Action[ApiV2ProjectTemplate] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF(parseCirce.decodeJson[ApiV2ProjectTemplate]) {
      implicit request =>
        val user                = request.user.get
        val settings            = request.body
        implicit val lang: Lang = user.langOrDefault

        for {
          _ <- ZIO
            .fromOption(factory.hasUserUploadError(user))
            .flip
            .mapError(e => BadRequest(UserError(messagesApi(e))))
          canUpload <- {
            if (settings.ownerName == user.name) ZIO.succeed((user.id.value, true))
            else
              service
                .runDbCon(APIV2Queries.canUploadToOrg(user.id, settings.ownerName).option)
                .get
                .asError(BadRequest(ApiError("Owner not found")))
          }
          _ <- ZIO.unit.filterOrFail(_ => canUpload._2)(Forbidden(ApiError("Can't upload to that org")))
          project <- factory
            .createProject(canUpload._1, settings.ownerName, settings.asFactoryTemplate)
            .mapError(e => BadRequest(UserError(messagesApi(e))))
          _ <- projects.refreshHomePage(MDCLogger)
        } yield {

          Created(
            APIV2.Project(
              project.createdAt,
              project.pluginId,
              project.name,
              APIV2.ProjectNamespace(project.ownerName, project.slug),
              Nil,
              APIV2.ProjectStatsAll(
                views = 0,
                downloads = 0,
                recentViews = 0,
                recentDownloads = 0,
                stars = 0,
                watchers = 0
              ),
              project.category,
              project.description,
              project.createdAt,
              project.visibility,
              APIV2.UserActions(starred = false, watching = false),
              APIV2.ProjectSettings(
                project.settings.homepage,
                project.settings.issues,
                project.settings.source,
                project.settings.support,
                APIV2.ProjectLicense(
                  project.settings.licenseName,
                  project.settings.licenseUrl
                ),
                project.settings.forumSync
              ),
              _root_.controllers.project.routes.Projects.showIcon(project.ownerName, project.slug).absoluteURL()
            )
          )
        }
    }

  def showProject(pluginId: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      val dbCon = APIV2Queries
        .singleProjectQuery(pluginId, request.globalPermissions.has(Permission.SeeHidden), request.user.map(_.id))
        .option

      service.runDbCon(dbCon).get.flatten.bimap(_ => NotFound, Ok(_))
    }

  def showProjectDescription(pluginId: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF {
      service.runDbCon(APIV2Queries.getPage(pluginId, "Home").option).get.asError(NotFound).map {
        case (_, _, _, contents) =>
          Ok(Json.obj("description" := contents))
      }
    }

  def editProject(pluginId: String): Action[Json] =
    ApiAction(Permission.EditProjectSettings, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.json) { implicit request =>
        val root = request.body.hcursor

        val res: Decoder.AccumulatingResult[EditableProject] = EditableProjectF.patchDecoder.traverseKC(
          λ[PatchDecoder ~>: Compose2[Decoder.AccumulatingResult, Option, *]](_.decode(root))
        )

        res match {
          case Validated.Valid(a) =>
            //Renaming a project is a big deal, and can't be done as easily as most other things
            val withoutName = a.copy[Option](
              name = None,
              settings = a.settings.copy[Option](keywords = a.settings.keywords.map(_.distinct))
            )

            val renameOp = a.name.fold(ZIO.unit: ZIO[Any, Result, Unit]) { newName =>
              projects
                .withPluginId(pluginId)
                .get
                .orDieWith(_ => new Exception("impossible"))
                .flatMap(projects.rename(_, newName).absolve)
                .mapError(e => BadRequest(ApiError(e)))
            }

            renameOp *> service
              .runDbCon(
                //We need two queries two queries as we use the generic update function
                APIV2Queries.updateProject(pluginId, withoutName).run *> APIV2Queries
                  .singleProjectQuery(
                    pluginId,
                    request.globalPermissions.has(Permission.SeeHidden),
                    request.user.map(_.id)
                  )
                  .unique
              )
              .flatten
              .map(Ok(_))
          case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e.map(_.show))))
        }
      }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF {
      service
        .runDbCon(
          APIV2Queries
            .projectMembers(pluginId, limitOrDefault(limit, 25), offsetOrZero(offset))
            .to[Vector]
        )
        .map(xs => Ok(xs.asJson))
    }

  def showProjectStats(pluginId: String, fromDateString: String, toDateString: String): Action[AnyContent] =
    CachingApiAction(Permission.IsProjectMember, APIScope.ProjectScope(pluginId)).asyncF {
      import Ordering.Implicits._

      def parseDate(dateStr: String) =
        Validated
          .catchOnly[DateTimeParseException](LocalDate.parse(dateStr))
          .leftMap(_ => ApiErrors(NonEmptyList.one(s"Badly formatted date $dateStr")))

      for {
        t <- ZIO
          .fromEither(parseDate(fromDateString).product(parseDate(toDateString)).toEither)
          .mapError(BadRequest(_))
        (fromDate, toDate) = t
        _ <- ZIO.unit.filterOrFail(_ => fromDate < toDate)(BadRequest(ApiError("From date is after to date")))
        res <- service.runDbCon(
          APIV2Queries.projectStats(pluginId, fromDate, toDate).to[Vector].map(APIV2ProjectStatsQuery.asProtocol)
        )
      } yield Ok(res.asJson)
    }
}
object Projects {
  import APIV2.categoryCodec

  @SnakeCaseJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  type EditableProject = EditableProjectF[Option]
  case class EditableProjectF[F[_]](
      name: F[String],
      ownerName: F[String],
      category: F[Category],
      summary: F[Option[String]],
      settings: EditableProjectSettingsF[F]
  )
  object EditableProjectF {
    implicit val F
        : ApplicativeKC[EditableProjectF] with TraverseKC[EditableProjectF] with DistributiveKC[EditableProjectF] =
      Derive.allKC[EditableProjectF]

    val patchDecoder: EditableProjectF[PatchDecoder] =
      PatchDecoder.fromName(Derive.namesWithProductImplicitsC[EditableProjectF, Decoder])(
        io.circe.derivation.renaming.snakeCase
      )
  }

  case class EditableProjectSettingsF[F[_]](
      keywords: F[List[String]],
      homepage: F[Option[String]],
      issues: F[Option[String]],
      sources: F[Option[String]],
      support: F[Option[String]],
      license: EditableProjectLicenseF[F],
      forumSync: F[Boolean]
  )
  object EditableProjectSettingsF {
    implicit val F: ApplicativeKC[EditableProjectSettingsF]
      with TraverseKC[EditableProjectSettingsF]
      with DistributiveKC[EditableProjectSettingsF] = Derive.allKC[EditableProjectSettingsF]
  }

  case class EditableProjectLicenseF[F[_]](name: F[Option[String]], url: F[Option[String]])
  object EditableProjectLicenseF {
    implicit val F: ApplicativeKC[EditableProjectLicenseF]
      with TraverseKC[EditableProjectLicenseF]
      with DistributiveKC[EditableProjectLicenseF] = Derive.allKC[EditableProjectLicenseF]
  }

  @SnakeCaseJsonCodec case class ApiV2ProjectTemplate(
      name: String,
      pluginId: String,
      category: Category,
      description: Option[String],
      ownerName: String
  ) {

    def asFactoryTemplate: ProjectTemplate = ProjectTemplate(name, pluginId, category, description)
  }
}
