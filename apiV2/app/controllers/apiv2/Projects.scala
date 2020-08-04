package controllers.apiv2

import java.time.LocalDate
import java.time.format.DateTimeParseException

import play.api.http.HttpErrorHandler
import play.api.i18n.Lang
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, Result}

import controllers.OreControllerComponents
import controllers.apiv2.helpers._
import controllers.sugar.Requests.ApiRequest
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.APIV2ProjectStatsQuery
import models.viewhelper.ProjectData
import ore.OreConfig
import ore.data.project.Category
import ore.data.user.notification.NotificationType
import ore.db.Model
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.ProjectRoleTable
import ore.models.Job
import ore.models.project.factory.{ProjectFactory, ProjectTemplate}
import ore.models.project.{Project, ProjectSortingStrategy, Version, Visibility}
import ore.models.user.role.ProjectUserRole
import ore.models.user.{LoggedActionProject, LoggedActionType, Notification}
import ore.permission.Permission
import ore.util.OreMDC
import util.syntax._
import util.{PartialUtils, PatchDecoder, UserActionLogger}

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.all._
import io.circe._
import io.circe.derivation.annotations.SnakeCaseJsonCodec
import io.circe.syntax._
import squeal.category._
import squeal.category.macros.Derive
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

class Projects(
    factory: ProjectFactory,
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Projects._

  def listProjects(
      q: Option[String],
      categories: Seq[Category],
      platforms: Seq[String],
      stability: Seq[Version.Stability],
      owner: Option[String],
      sort: Option[ProjectSortingStrategy],
      relevance: Option[Boolean],
      exact: Option[Boolean],
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
          exact.getOrElse(false),
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
          request.user.map(_.id),
          exact.getOrElse(false)
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
          canUpload <- {
            if (settings.ownerName == user.name) ZIO.succeed((user.id.value, true))
            else
              service
                .runDbCon(APIV2Queries.canUploadToOrg(user.id, settings.ownerName).option)
                .get
                .orElseFail(BadRequest(ApiError("Owner not found")))
          }
          _ <- ZIO.unit.filterOrFail(_ => canUpload._2)(Forbidden(ApiError("Can't upload to that org")))
          project <- factory
            .createProject(canUpload._1, settings.ownerName, settings.asFactoryTemplate)
            .mapError(e => BadRequest(UserError(messagesApi(e))))
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
                project.settings.keywords,
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
              _root_.controllers.project.routes.Projects.showIcon(project.ownerName, project.slug).absoluteURL(),
              APIV2.ProjectExternal(
                APIV2.ProjectExternalDiscourse(
                  None,
                  None
                )
              )
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
      service.runDbCon(APIV2Queries.getPage(pluginId, "Home").option).get.orElseFail(NotFound).map {
        case (_, _, _, contents) =>
          Ok(Json.obj("description" := contents))
      }
    }

  def editProject(pluginId: String): Action[Json] =
    ApiAction(Permission.EditProjectSettings, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.json) { implicit request =>
        val res: ValidatedNel[String, EditableProject] = PartialUtils.decodeAndValidate(
          EditableProjectF.patchDecoder,
          EditableProjectF.validation,
          request.body.hcursor
        )

        res match {
          case Validated.Valid(edits) =>
            //Renaming or transferring a project is a big deal, and can't be done as easily as most other things
            val withoutNameAndOwner = edits.copy[Option](
              name = None,
              namespace = EditableProjectNamespaceF[Option](None)
            )

            def checkIsOwner(action: String): ZIO[Any, Result, Unit] =
              if (request.scopePermission.has(Permission.IsProjectOwner)) ZIO.unit
              else ZIO.fail(Forbidden(ApiError(s"Not enough perms to $action")))

            val renameOp = edits.name.fold(ZIO.unit: ZIO[Any, Result, Unit]) { newName =>
              val doRename = projects
                .withPluginId(pluginId)
                .get
                .orDieWith(_ => new Exception("impossible"))
                .flatMap(projects.rename(_, newName).absolve)
                .mapError(e => BadRequest(ApiError(e)))

              checkIsOwner("rename project") *> doRename
            }

            val transferOp = edits.namespace.owner.fold(ZIO.unit: ZIO[Any, Result, Unit]) { newOwner =>
              val doTransfer = for {
                project <- projects.withPluginId(pluginId).someOrFail(NotFound)
                user    <- users.withName(newOwner)(OreMDC.NoMDC).value.someOrFail(NotFound)
                userRole <- project
                  .memberships[Task, ProjectUserRole, ProjectRoleTable]
                  .getMembership(project)(user.id)
                  .orDie
                  .someOrFail(BadRequest(ApiError("User to transfer to is not member")))
                _ <- if (userRole.isAccepted) ZIO.unit
                else ZIO.fail(BadRequest(ApiError("User to transfer to has not accepted invite")))
                _ <- if (userRole.role == ore.permission.role.Role.ProjectAdmin) ZIO.unit
                else ZIO.fail(BadRequest(ApiError("User to transfer to is not project admin")))
                _ <- projects.transfer(project, user.id)
              } yield ()

              checkIsOwner("transfer project") *> doTransfer
            }

            val update = service.runDbCon(APIV2Queries.updateProject(pluginId, withoutNameAndOwner).run)

            //We need two queries two queries as we use the generic update function
            val get = service
              .runDbCon(
                APIV2Queries
                  .singleProjectQuery(
                    pluginId,
                    request.globalPermissions.has(Permission.SeeHidden),
                    request.user.map(_.id)
                  )
                  .unique
              )
              .flatten
              .map(Ok(_))

            val count = PartialUtils.countDefined(edits)
            count match {
              case 0                                                            => ZIO.fail(BadRequest(ApiError("No updates defined")))
              case 2 if edits.name.isDefined && edits.namespace.owner.isDefined => renameOp *> transferOp *> get
              case 1 if edits.name.isDefined                                    => renameOp *> get
              case 1 if edits.namespace.owner.isDefined                         => transferOp *> get
              case _                                                            => renameOp *> transferOp *> update *> get
            }
          case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e)))
        }
      }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit r =>
      Members.membersAction(APIV2Queries.projectMembers(pluginId, _, _), limit, offset)
    }

  def updateMembers(pluginId: String): Action[List[Members.MemberUpdate]] =
    ApiAction(Permission.ManageProjectMembers, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.decodeJson[List[Members.MemberUpdate]]) { implicit r =>
        Members.updateMembers[Project, ProjectUserRole, ProjectRoleTable](
          getOwner = projects.withPluginId(pluginId).someOrFail(NotFound),
          getMembersQuery = APIV2Queries.projectMembers(pluginId, _, _),
          createRole = ProjectUserRole(_, _, _),
          roleCompanion = ProjectUserRole,
          notificationType = NotificationType.ProjectInvite,
          notificationLocalization = "notification.project.invite"
        )
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

  def setProjectVisibility(pluginId: String): Action[EditVisibility] =
    ApiAction(Permission.None, APIScope.ProjectScope(pluginId)).asyncF(parseCirce.decodeJson[EditVisibility]) {
      implicit request =>
        projects.withPluginId(pluginId).someOrFail(NotFound).flatMap { project =>
          request.body.process(
            project,
            request.user.get.id,
            request.scopePermission,
            Permission.DeleteProject,
            service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob).unit,
            doHardDeleteProject,
            (newV, oldV) =>
              UserActionLogger
                .logApi(
                  request,
                  LoggedActionType.ProjectVisibilityChange,
                  project.id,
                  newV,
                  oldV
                )(LoggedActionProject.apply)
                .unit
          )
        }
    }

  def projectData(pluginId: String): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit r =>
      for {
        project <- projects.withPluginId(pluginId).get.orElseFail(NotFound)
        data    <- ProjectData.of[ZIO[Blocking, Throwable, *]](project).orDie
      } yield Ok(
        Json.obj(
          "flagCount" := data.flagCount,
          "noteCount" := data.noteCount,
          "lastVisibilityChange" := data.lastVisibilityChange.map { change =>
            Json.obj(
              "comment" := change.comment
            )
          },
          "lastVisibilityChangeUser" := data.lastVisibilityChangeUser
        )
      )
    }

  private def doHardDeleteProject(project: Model[Project])(implicit request: ApiRequest[_]): UIO[Unit] = {
    projects.delete(project).unit <* UserActionLogger.logApiOption(
      request,
      LoggedActionType.ProjectVisibilityChange,
      None,
      "deleted",
      project.visibility.nameKey
    )(LoggedActionProject.apply)
  }

  def hardDeleteProject(pluginId: String): Action[AnyContent] =
    ApiAction(Permission.HardDeleteProject, APIScope.ProjectScope(pluginId)).asyncF { implicit r =>
      projects
        .withPluginId(pluginId)
        .someOrFail(NotFound)
        .flatMap(doHardDeleteProject(_))
        .as(NoContent)
    }

  def editDiscourseSettings(pluginId: String): Action[Projects.DiscourseModifyTopicSettings] =
    ApiAction(Permission.EditAdminSettings, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.decodeJson[Projects.DiscourseModifyTopicSettings]) { implicit request =>
        projects
          .withPluginId(pluginId)
          .someOrFail(NotFound)
          .flatMap { project =>
            val update = service.update(project)(_.copy(topicId = request.body.topicId, postId = request.body.postId))
            val addJob = service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob)

            update.as(NoContent) <* addJob.when(request.body.updateTopic)
          }
      }
}
object Projects {
  import APIV2.{categoryCodec, visibilityCodec, permissionRoleCodec}

  @SnakeCaseJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  type EditableProject = EditableProjectF[Option]
  case class EditableProjectF[F[_]](
      name: F[String],
      namespace: EditableProjectNamespaceF[F],
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

    def validation(implicit config: OreConfig): EditableProjectF[PartialUtils.Validator] = {
      import PartialUtils.Validator
      import PartialUtils.Validator._

      EditableProjectF[Validator](
        checkLength("project name", config.ore.projects.maxDescLen),
        EditableProjectNamespaceF[Validator](noValidation),
        noValidation,
        allValid(invaidIfEmpty("summary"), validIfEmpty(checkLength("summary", config.ore.projects.maxDescLen))),
        EditableProjectSettingsF[Validator](
          allValid(
            seq => Validated.condNel(seq.lengthIs > 5, seq, "Too many keywords provided"),
            seq => Validated.condNel(seq.contains(""), seq, "Found keywords with empty strings"),
            seq => Validated.condNel(seq.distinct == seq, seq, "Found duplicate keywords")
          ),
          invaidIfEmpty("homepage"),
          invaidIfEmpty("issues"),
          invaidIfEmpty("sources"),
          invaidIfEmpty("support"),
          EditableProjectLicenseF[Validator](
            invaidIfEmpty("license name"),
            invaidIfEmpty("license url")
          ),
          noValidation
        )
      )
    }
  }

  case class EditableProjectNamespaceF[F[_]](
      owner: F[String]
  )
  object EditableProjectNamespaceF {
    implicit val F: ApplicativeKC[EditableProjectNamespaceF]
      with TraverseKC[EditableProjectNamespaceF]
      with DistributiveKC[EditableProjectNamespaceF] = Derive.allKC[EditableProjectNamespaceF]
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

  @SnakeCaseJsonCodec case class DiscourseModifyTopicSettings(
      topicId: Option[Int],
      postId: Option[Int],
      updateTopic: Boolean
  )
}
