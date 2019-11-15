package controllers.apiv2

import java.time.LocalDate
import java.time.format.DateTimeParseException

import play.api.http.HttpErrorHandler
import play.api.i18n.Lang
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors, Pagination, UserError}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.APIV2ProjectStatsQuery
import ore.data.project.Category
import ore.db.Model
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.ProjectSortingStrategy
import ore.models.project.factory.{ProjectFactory, ProjectTemplate}
import ore.models.user.User
import ore.permission.Permission
import ore.util.OreMDC
import util.syntax._

import cats.data.{NonEmptyList, Validated}
import cats.syntax.all._
import io.circe._
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.syntax._
import com.typesafe.scalalogging
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
      tags: Seq[String],
      owner: Option[String],
      sort: Option[ProjectSortingStrategy],
      relevance: Option[Boolean],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF("listProjects")(q, categories, tags, owner, sort, relevance, limit, offset) {
        val realLimit  = limitOrDefault(limit, config.ore.projects.initLoad)
        val realOffset = offsetOrZero(offset)

        val parsedTags = tags.map { s =>
          val splitted = s.split(":", 2)
          (splitted(0), splitted.lift(1))
        }

        val getProjects = APIV2Queries
          .projectQuery(
            None,
            categories.toList,
            parsedTags.toList,
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
            parsedTags.toList,
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
    }

  private def orgasUserCanUploadTo(user: Model[User]): UIO[Set[String]] = {
    import cats.instances.vector._
    for {
      all <- user.organizations.allFromParent
      canCreate <- all.toVector.parTraverse(
        org => user.permissionsIn(org).map(_.has(Permission.CreateProject)).tupleLeft(org.name)
      )
    } yield {
      // Filter by can Create Project
      val others = canCreate.collect {
        case (name, true) => name
      }

      others.toSet + user.name // Add self
    }
  }

  //TODO: Check if we need another scope her to accommodate organizations
  def createProject(): Action[ApiV2ProjectTemplate] =
    ApiAction(Permission.CreateProject, APIScope.GlobalScope).asyncF(parseCirce.decodeJson[ApiV2ProjectTemplate]) {
      implicit request =>
        val user                = request.user.get
        val settings            = request.body
        implicit val lang: Lang = user.langOrDefault

        for {
          _ <- ZIO
            .fromOption(factory.hasUserUploadError(user))
            .flip
            .mapError(e => BadRequest(UserError(messagesApi(e))))
          _ <- orgasUserCanUploadTo(user).filterOrFail(_.contains(settings.ownerName))(
            BadRequest(ApiError("Can't upload to that organization"))
          )
          owner <- {
            if (settings.ownerName == user.name) ZIO.succeed(user)
            else
              ModelView
                .now(User)
                .find(_.name === settings.ownerName)
                .toZIOWithError(BadRequest(ApiError("User not found, or can't upload to that user")))
          }
          project <- factory
            .createProject(owner, settings.asFactoryTemplate)
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
              APIV2.ProjectStatsAll(0, 0, 0, 0, 0, 0),
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
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("showProject")(pluginId) {
        val dbCon = APIV2Queries
          .projectQuery(
            Some(pluginId),
            Nil,
            Nil,
            None,
            None,
            request.globalPermissions.has(Permission.SeeHidden),
            request.user.map(_.id),
            ProjectSortingStrategy.Default,
            orderWithRelevance = false,
            1,
            0
          )
          .option

        service.runDbCon(dbCon).get.flatMap(identity).bimap(_ => NotFound, Ok(_))
      }
    }

  def editProject(pluginId: String): Action[Json] =
    ApiAction(Permission.EditProjectSettings, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.json) { implicit request =>
        val root     = request.body.hcursor
        val settings = root.downField("settings")

        val res = (
          withUndefined[String](root.downField("name")),
          withUndefined[String](root.downField("owner_name")),
          withUndefined[Category](root.downField("category"))(
            Decoder[String].emap(Category.fromApiName(_).toRight("Not a valid category name"))
          ),
          withUndefined[Option[String]](root.downField("description")),
          withUndefined[Option[String]](settings.downField("homepage")),
          withUndefined[Option[String]](settings.downField("issues")),
          withUndefined[Option[String]](settings.downField("sources")),
          withUndefined[Option[String]](settings.downField("support")),
          withUndefined[APIV2.ProjectLicense](settings.downField("license")),
          withUndefined[Boolean](settings.downField("forum_sync"))
        ).mapN(EditableProject.apply)

        res match {
          case Validated.Valid(a)   => ???
          case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e.map(_.show))))
        }
      }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("showMembers")(pluginId, limit, offset) {
        service
          .runDbCon(
            APIV2Queries
              .projectMembers(pluginId, limitOrDefault(limit, 25), offsetOrZero(offset))
              .to[Vector]
          )
          .map(xs => Ok(xs.asJson))
      }
    }

  def showProjectStats(pluginId: String, fromDateString: String, toDateString: String): Action[AnyContent] =
    ApiAction(Permission.IsProjectMember, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("projectStats")(pluginId, fromDateString, toDateString) {
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
}
object Projects {
  import APIV2.{circeConfig, categoryCodec}

  @ConfiguredJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  case class EditableProject(
      name: Option[String],
      ownerName: Option[String],
      category: Option[Category],
      description: Option[Option[String]],
      homepage: Option[Option[String]],
      issues: Option[Option[String]],
      sources: Option[Option[String]],
      support: Option[Option[String]],
      license: Option[APIV2.ProjectLicense],
      forumSync: Option[Boolean]
  )

  @ConfiguredJsonCodec case class ApiV2ProjectTemplate(
      name: String,
      pluginId: String,
      category: Category,
      description: Option[String],
      ownerName: String
  ) {

    def asFactoryTemplate: ProjectTemplate = ProjectTemplate(name, pluginId, category, description)
  }
}
