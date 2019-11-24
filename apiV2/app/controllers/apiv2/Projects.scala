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
import util.PatchDecoder
import util.fp.{ApplicativeK, TraverseK}
import util.syntax._

import cats.data.{NonEmptyList, Tuple2K, Validated}
import cats.syntax.all._
import cats.tagless.syntax.all._
import cats.{Applicative, ~>}
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
      platforms: Seq[String],
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

  def editProject(pluginId: String): Action[Json] =
    ApiAction(Permission.EditProjectSettings, APIScope.ProjectScope(pluginId))
      .asyncF(parseCirce.json) { implicit request =>
        val root = request.body.hcursor

        val res: Decoder.AccumulatingResult[EditableProject] = EditableProjectF.patchDecoder.traverseK(
          λ[PatchDecoder ~> λ[A => Decoder.AccumulatingResult[Option[A]]]](_.decode(root))
        )

        res match {
          case Validated.Valid(a) =>
            service
              .runDbCon(
                //We need two queries two queries as singleProjectQuery takes data from the home_projects view
                APIV2Queries.updateProject(pluginId, a).run *> APIV2Queries
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
  import APIV2.{circeConfig, categoryCodec}

  @ConfiguredJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  type EditableProject = EditableProjectF[Option]
  case class EditableProjectF[F[_]](
      name: F[String],
      ownerName: F[String],
      category: F[Category],
      description: F[Option[String]],
      homepage: F[Option[String]],
      issues: F[Option[String]],
      sources: F[Option[String]],
      support: F[Option[String]],
      license: EditableProjectLicenseF[F],
      forumSync: F[Boolean]
  )
  object EditableProjectF {
    val patchDecoder: EditableProjectF[PatchDecoder] = EditableProjectF(
      PatchDecoder.mkPath[String]("name"),
      PatchDecoder.mkPath[String]("owner_name"),
      PatchDecoder.mkPath[Category]("category"),
      PatchDecoder.mkPath[Option[String]]("description"),
      PatchDecoder.mkPath[Option[String]]("settings", "homepage"),
      PatchDecoder.mkPath[Option[String]]("settings", "issues"),
      PatchDecoder.mkPath[Option[String]]("settings", "sources"),
      PatchDecoder.mkPath[Option[String]]("settings", "support"),
      EditableProjectLicenseF(
        PatchDecoder.mkPath[Option[String]]("settings", "license", "name"),
        PatchDecoder.mkPath[Option[String]]("settings", "license", "url")
      ),
      PatchDecoder.mkPath[Boolean]("settings", "forum_sync")
    )

    implicit val applicativeTraverseK: ApplicativeK[EditableProjectF] with TraverseK[EditableProjectF] =
      new ApplicativeK[EditableProjectF] with TraverseK[EditableProjectF] {
        override def pure[A[_]](a: shapeless.Const[Unit]#λ ~> A): EditableProjectF[A] = EditableProjectF(
          a.apply(()),
          a.apply(()),
          a.apply(()),
          a.apply(()),
          a.apply(()),
          a.apply(()),
          a.apply(()),
          a.apply(()),
          EditableProjectLicenseF.applicativeTraverseK.pure(a),
          a.apply(())
        )

        override def traverseK[G[_]: Applicative, A[_], B[_]](fa: EditableProjectF[A])(
            f: A ~> λ[C => G[B[C]]]
        ): G[EditableProjectF[B]] =
          (
            f(fa.name),
            f(fa.ownerName),
            f(fa.category),
            f(fa.description),
            f(fa.homepage),
            f(fa.issues),
            f(fa.sources),
            f(fa.support),
            fa.license.traverseK(f),
            f(fa.forumSync)
          ).mapN(EditableProjectF.apply)

        override def productK[F[_], G[_]](
            af: EditableProjectF[F],
            ag: EditableProjectF[G]
        ): EditableProjectF[Tuple2K[F, G, *]] = EditableProjectF(
          Tuple2K(af.name, ag.name),
          Tuple2K(af.ownerName, ag.ownerName),
          Tuple2K(af.category, ag.category),
          Tuple2K(af.description, ag.description),
          Tuple2K(af.homepage, ag.homepage),
          Tuple2K(af.issues, ag.issues),
          Tuple2K(af.sources, ag.sources),
          Tuple2K(af.support, ag.support),
          af.license.productK(ag.license),
          Tuple2K(af.forumSync, ag.forumSync)
        )

        override def foldLeftK[A[_], B](fa: EditableProjectF[A], b: B)(f: B => A ~> shapeless.Const[B]#λ): B = {
          val b1 = f(b)(fa.name)
          val b2 = f(b1)(fa.ownerName)
          val b3 = f(b2)(fa.category)
          val b4 = f(b3)(fa.description)
          val b5 = f(b4)(fa.homepage)
          val b6 = f(b5)(fa.issues)
          val b7 = f(b6)(fa.sources)
          val b8 = f(b7)(fa.support)
          val b9 = fa.license.foldLeftK(b8)(f)
          f(b9)(fa.forumSync)
        }
      }
  }

  case class EditableProjectLicenseF[F[_]](name: F[Option[String]], url: F[Option[String]])
  object EditableProjectLicenseF {
    implicit val applicativeTraverseK: ApplicativeK[EditableProjectLicenseF] with TraverseK[EditableProjectLicenseF] =
      new ApplicativeK[EditableProjectLicenseF] with TraverseK[EditableProjectLicenseF] {
        override def pure[A[_]](a: shapeless.Const[Unit]#λ ~> A): EditableProjectLicenseF[A] =
          EditableProjectLicenseF(a.apply(()), a.apply(()))

        override def traverseK[G[_]: Applicative, A[_], B[_]](fa: EditableProjectLicenseF[A])(
            f: A ~> λ[C => G[B[C]]]
        ): G[EditableProjectLicenseF[B]] =
          (f(fa.name), f(fa.url)).mapN(EditableProjectLicenseF.apply)

        override def productK[F[_], G[_]](
            af: EditableProjectLicenseF[F],
            ag: EditableProjectLicenseF[G]
        ): EditableProjectLicenseF[Tuple2K[F, G, *]] =
          EditableProjectLicenseF(Tuple2K(af.name, ag.name), Tuple2K(af.name, ag.name))

        override def foldLeftK[A[_], B](fa: EditableProjectLicenseF[A], b: B)(f: B => A ~> shapeless.Const[B]#λ): B = {
          val b1 = f(b)(fa.name)
          f(b1)(fa.url)
        }
      }
  }

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
