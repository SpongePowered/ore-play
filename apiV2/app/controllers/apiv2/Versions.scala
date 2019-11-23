package controllers.apiv2

import java.nio.file.Path
import java.time.{LocalDate, OffsetDateTime}
import java.time.format.DateTimeParseException

import scala.jdk.CollectionConverters._

import play.api.http.HttpErrorHandler
import play.api.i18n.Lang
import play.api.inject.ApplicationLifecycle
import play.api.libs.Files
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors, Pagination, UserError, UserErrors}
import controllers.sugar.Requests.ApiRequest
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.{APIV2QueryVersion, APIV2VersionStatsQuery}
import ore.db.Model
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTable, VersionTable}
import ore.models.project.Version.Stability
import ore.models.project.{Page, Project, ReviewState, Version, Visibility}
import ore.models.project.factory.ProjectFactory
import ore.models.project.io.{PluginFileWithData, PluginUpload}
import ore.models.user.User
import ore.permission.Permission
import util.PatchDecoder
import util.fp.{ApplicativeK, TraverseK}
import util.syntax._

import cats.data.{NonEmptyList, Tuple2K, Validated}
import cats.syntax.all._
import cats.{Applicative, ~>}
import io.circe._
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.syntax._
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{IO, UIO, ZIO}

class Versions(
    factory: ProjectFactory,
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Versions._

  def listVersions(
      pluginId: String,
      tags: Seq[String],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      val realLimit  = limitOrDefault(limit, config.ore.projects.initVersionLoad.toLong)
      val realOffset = offsetOrZero(offset)
      val getVersions = APIV2Queries
        .versionQuery(
          pluginId,
          None,
          tags.toList,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id),
          realLimit,
          realOffset
        )
        .to[Vector]

      val countVersions = APIV2Queries
        .versionCountQuery(
          pluginId,
          tags.toList,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id)
        )
        .unique

      (service.runDbCon(getVersions), service.runDbCon(countVersions)).parMapN { (versions, count) =>
        Ok(
          PaginatedVersionResult(
            Pagination(realLimit, realOffset, count),
            versions
          )
        )
      }
    }

  def showVersion(pluginId: String, name: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      service
        .runDbCon(
          APIV2Queries
            .singleVersionQuery(
              pluginId,
              name,
              request.globalPermissions.has(Permission.SeeHidden),
              request.user.map(_.id)
            )
            .option
        )
        .map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
    }

  def editVersion(pluginId: String, name: String): Action[Json] =
    ApiAction(Permission.EditVersion, APIScope.ProjectScope(pluginId)).asyncF(parseCirce.json) { implicit request =>
      val root = request.body.hcursor

      val res: Decoder.AccumulatingResult[EditableVersion] = EditableVersionF.patchDecoder.traverseK(
        λ[PatchDecoder ~> λ[A => Decoder.AccumulatingResult[Option[A]]]](_.decode(root))
      )

      res match {
        case Validated.Valid(a) =>
          service
            .runDbCon(
              //We need two queries two queries as singleProjectQuery takes data from the home_projects view
              //TODO: Not true for version
              APIV2Queries.updateVersion(pluginId, name, a).run *> APIV2Queries
                .singleVersionQuery(
                  pluginId,
                  name,
                  request.globalPermissions.has(Permission.SeeHidden),
                  request.user.map(_.id)
                )
                .unique
            )
            .map(Ok(_))

        case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e.map(_.show))))
      }
    }

  def showVersionDescription(pluginId: String, name: String): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF {
      service
        .runDBIO(
          TableQuery[ProjectTable]
            .join(TableQuery[VersionTable])
            .on(_.id === _.projectId)
            .filter(t => t._1.pluginId === pluginId && t._2.versionString === name)
            .map(_._2.description)
            .result
            .headOption
        )
        .map(_.fold(NotFound: Result)(a => Ok(APIV2.VersionDescription(a))))
    }

  def showVersionStats(
      pluginId: String,
      version: String,
      fromDateString: String,
      toDateString: String
  ): Action[AnyContent] =
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
          APIV2Queries
            .versionStats(pluginId, version, fromDate, toDate)
            .to[Vector]
            .map(APIV2VersionStatsQuery.asProtocol)
        )
      } yield Ok(res.asJson)
    }

  //TODO: Do the async part at some point
  private def readFileAsync(file: Path): ZIO[Blocking, Throwable, String] = {
    import zio.blocking._
    effectBlocking(java.nio.file.Files.readAllLines(file).asScala.mkString("\n"))
  }

  private def processVersionUploadToErrors(pluginId: String)(
      implicit request: ApiRequest[MultipartFormData[Files.TemporaryFile]]
  ): ZIO[Blocking, Result, (Model[User], Model[Project], PluginFileWithData)] = {
    val fileF = ZIO.fromEither(
      request.body.file("plugin-file").toRight(BadRequest(ApiError("No plugin file specified")))
    )

    for {
      user    <- ZIO.fromOption(request.user).asError(BadRequest(ApiError("No user found for session")))
      project <- projects.withPluginId(pluginId).get.asError(NotFound)
      file    <- fileF
      pluginFile <- factory
        .collectErrorsForVersionUpload(PluginUpload(file.ref, file.filename), user, project)
        .leftMap { s =>
          implicit val lang: Lang = user.langOrDefault
          BadRequest(UserError(messagesApi(s)))
        }
    } yield (user, project, pluginFile)
  }

  def scanVersion(pluginId: String): Action[MultipartFormData[Files.TemporaryFile]] =
    ApiAction(Permission.CreateVersion, APIScope.ProjectScope(pluginId))(parse.multipartFormData).asyncF {
      implicit request =>
        for {
          t <- processVersionUploadToErrors(pluginId)
          (user, _, pluginFile) = t
        } yield {
          val apiVersion = APIV2QueryVersion(
            OffsetDateTime.now(),
            pluginFile.versionString,
            pluginFile.dependencies.toList,
            Visibility.Public,
            0,
            pluginFile.fileSize,
            pluginFile.md5,
            pluginFile.fileName,
            Some(user.name),
            ReviewState.Unreviewed,
            pluginFile.data.containsMixins,
            Version.Stability.Stable,
            None
          )

          val warnings = NonEmptyList.fromList(pluginFile.warnings.toList)
          Ok(ScannedVersion(apiVersion.asProtocol, warnings))
        }
    }

  def deployVersion(pluginId: String): Action[MultipartFormData[Files.TemporaryFile]] =
    ApiAction(Permission.CreateVersion, APIScope.ProjectScope(pluginId))(parse.multipartFormData).asyncF {
      implicit request =>
        type TempFile = MultipartFormData.FilePart[Files.TemporaryFile]
        import zio.blocking._

        val pluginInfoFromFileF = ZIO.bracket(
          acquire = UIO(request.body.file("plugin-info")).get.mapError(Left.apply),
          release = (filePart: TempFile) => effectBlocking(java.nio.file.Files.deleteIfExists(filePart.ref)).fork,
          use = (filePart: TempFile) => readFileAsync(filePart.ref).mapError(Right.apply)
        )

        val dataStringF = ZIO
          .fromOption(request.body.dataParts.get("plugin-info").flatMap(_.headOption))
          .orElse(pluginInfoFromFileF)
          .catchAll {
            case Left(_)  => IO.fail("No plugin info specified")
            case Right(e) => IO.die(e)
          }

        val dataF = dataStringF
          .flatMap(s => ZIO.fromEither(parser.decode[DeployVersionInfo](s).leftMap(_.show)))
          .ensure("Description too long")(_.description.forall(_.length < Page.maxLength))
          .mapError(e => BadRequest(ApiError(e)))

        for {
          t <- processVersionUploadToErrors(pluginId)
          (user, project, pluginFile) = t
          data <- dataF
          t <- factory
            .createVersion(
              project,
              pluginFile,
              data.description,
              data.createForumPost.getOrElse(project.settings.forumSync),
              data.stability.getOrElse(Stability.Stable),
              data.releaseType
            )
            .mapError { es =>
              implicit val lang: Lang = user.langOrDefault
              BadRequest(UserErrors(es.map(messagesApi(_))))
            }
        } yield {
          val (_, version) = t

          val apiVersion = APIV2QueryVersion(
            version.createdAt,
            version.versionString,
            version.dependencyIds,
            version.visibility,
            0,
            version.fileSize,
            version.hash,
            version.fileName,
            Some(user.name),
            version.reviewState,
            version.tags.usesMixin,
            version.tags.stability,
            version.tags.releaseType
          )

          Created(apiVersion.asProtocol)
        }
    }
}
object Versions {
  import APIV2.circeConfig

  //TODO: Allow setting multiple platforms
  @ConfiguredJsonCodec case class DeployVersionInfo(
      createForumPost: Option[Boolean],
      description: Option[String],
      stability: Option[Version.Stability],
      releaseType: Option[Version.ReleaseType]
  )

  @ConfiguredJsonCodec case class PaginatedVersionResult(
      pagination: Pagination,
      result: Seq[APIV2.Version]
  )

  //TODO: Allow setting multiple platforms
  type EditableVersion = EditableVersionF[Option]
  case class EditableVersionF[F[_]](
      description: F[Option[String]],
      stability: F[Version.Stability],
      releaseType: F[Option[Version.ReleaseType]]
  )
  object EditableVersionF {
    val patchDecoder: EditableVersionF[PatchDecoder] = EditableVersionF(
      PatchDecoder.mkPath[Option[String]]("description"),
      PatchDecoder.mkPath[Version.Stability]("stability"),
      PatchDecoder.mkPath[Option[Version.ReleaseType]]("release_type")
    )

    implicit val applicativeTraverseK: ApplicativeK[EditableVersionF] with TraverseK[EditableVersionF] =
      new ApplicativeK[EditableVersionF] with TraverseK[EditableVersionF] {
        override def pure[A[_]](a: shapeless.Const[Unit]#λ ~> A): EditableVersionF[A] = EditableVersionF(
          a(()),
          a(()),
          a(())
        )

        override def traverseK[G[_]: Applicative, A[_], B[_]](
            fa: EditableVersionF[A]
        )(f: A ~> λ[C => G[B[C]]]): G[EditableVersionF[B]] =
          (
            f(fa.description),
            f(fa.stability),
            f(fa.releaseType)
          ).mapN(EditableVersionF.apply)

        override def foldLeftK[A[_], B](fa: EditableVersionF[A], b: B)(f: B => A ~> shapeless.Const[B]#λ): B = {
          val b1 = f(b)(fa.description)
          val b2 = f(b1)(fa.stability)
          f(b2)(fa.releaseType)
        }

        override def productK[F[_], G[_]](
            af: EditableVersionF[F],
            ag: EditableVersionF[G]
        ): EditableVersionF[Tuple2K[F, G, *]] = EditableVersionF(
          Tuple2K(af.description, ag.description),
          Tuple2K(af.stability, ag.stability),
          Tuple2K(af.releaseType, ag.releaseType)
        )
      }
  }

  @ConfiguredJsonCodec case class ScannedVersion(
      version: APIV2.Version,
      warnings: Option[NonEmptyList[String]]
  )
}
