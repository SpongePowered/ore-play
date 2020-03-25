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
import ore.data.Platform
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
import util.syntax._

import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer, WriterT}
import cats.syntax.all._
import squeal.category._
import squeal.category.syntax.all._
import squeal.category.macros.Derive
import io.circe._
import io.circe.derivation.annotations.SnakeCaseJsonCodec
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
      platforms: Seq[String],
      stability: Seq[Version.Stability],
      releaseType: Seq[Version.ReleaseType],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    CachingApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { request =>
      val realLimit  = limitOrDefault(limit, config.ore.projects.initVersionLoad.toLong)
      val realOffset = offsetOrZero(offset)
      val parsedPlatforms = platforms.map { s =>
        val splitted = s.split(":", 2)
        (splitted(0), splitted.lift(1))
      }.toList

      val getVersions = APIV2Queries
        .versionQuery(
          pluginId,
          None,
          parsedPlatforms,
          stability.toList,
          releaseType.toList,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id),
          realLimit,
          realOffset
        )
        .to[Vector]

      val countVersions = APIV2Queries
        .versionCountQuery(
          pluginId,
          parsedPlatforms,
          stability.toList,
          releaseType.toList,
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
        .get
        .asError(NotFound)
        .map(a => Ok(a.asJson))
    }

  def editVersion(pluginId: String, name: String): Action[Json] =
    ApiAction(Permission.EditVersion, APIScope.ProjectScope(pluginId)).asyncF(parseCirce.json) { implicit request =>
      val root = request.body.hcursor
      import cats.instances.list._
      import cats.instances.option._

      def parsePlatforms(platforms: List[SimplePlatform]) = {
        platforms
          .traverse {
            case SimplePlatform(platformName, platformVersion) =>
              Platform
                .withValueOpt(platformName)
                .toValidNel(s"Don't know about the platform named $platformName")
                .tupleRight(platformVersion)
          }
          .map { ps =>
            ps.traverse {
              case (platform, version) =>
                platform
                  .produceVersionWarning(version)
                  .as(
                    (
                      platform.name,
                      version,
                      version.map(platform.coarseVersionOf)
                    )
                  )

            }
          }
          .nested
          .map(_.unzip3)
          .value
      }

      //We take the platform as flat in the API, but want it columnar.
      //We also want to verify the version and platform name, and get a coarse version
      val res: ValidatedNel[String, Writer[List[String], DbEditableVersion]] = EditableVersionF.patchDecoder
        .traverseKC(
          λ[PatchDecoder ~>: Compose2[Decoder.AccumulatingResult, Option, *]](_.decode(root))
        )
        .leftMap(_.map(_.show))
        .ensure(NonEmptyList.one("Description too long"))(_.description.flatten.forall(_.length < Page.maxLength))
        .andThen { a =>
          a.platforms
            .traverse(parsePlatforms)
            .map(_.sequence)
            .tupleLeft(a)
        }
        .map {
          case (a, w) =>
            w.map { optPlatforms =>
              DbEditableVersionF[Option](
                a.description,
                a.stability,
                a.releaseType,
                VersionedPlatformF[Option](
                  optPlatforms.map(_._1),
                  optPlatforms.map(_._2),
                  optPlatforms.map(_._3)
                )
              )
            }
        }

      res match {
        case Validated.Valid(WriterT((warnings, a))) =>
          service
            .runDbCon(
              //We need two queries as we use the generic update function
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

        case Validated.Invalid(e) => ZIO.fail(BadRequest(ApiErrors(e)))
      }
    }

  def showVersionChangelog(pluginId: String, name: String): Action[AnyContent] =
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
        .map(_.fold(NotFound: Result)(a => Ok(APIV2.VersionChangelog(a))))
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
            pluginFile.dependencyIds.toList,
            pluginFile.dependencyVersions.toList,
            Visibility.Public,
            0,
            pluginFile.fileSize,
            pluginFile.md5,
            pluginFile.fileName,
            Some(user.name),
            ReviewState.Unreviewed,
            pluginFile.data.containsMixins,
            Version.Stability.Stable,
            None,
            pluginFile.versionedPlatforms.map(_.id),
            pluginFile.versionedPlatforms.map(_.version)
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
          val (_, version, platforms) = t

          val apiVersion = APIV2QueryVersion(
            version.createdAt,
            version.versionString,
            version.dependencyIds,
            version.dependencyVersions,
            version.visibility,
            0,
            version.fileSize,
            version.hash,
            version.fileName,
            Some(user.name),
            version.reviewState,
            version.tags.usesMixin,
            version.tags.stability,
            version.tags.releaseType,
            platforms.map(_.platform).toList,
            platforms.map(_.platformVersion).toList
          )

          Created(apiVersion.asProtocol)
        }
    }
}
object Versions {

  //TODO: Allow setting multiple platforms
  @SnakeCaseJsonCodec case class DeployVersionInfo(
      createForumPost: Option[Boolean],
      description: Option[String],
      stability: Option[Version.Stability],
      releaseType: Option[Version.ReleaseType]
  )

  @SnakeCaseJsonCodec case class PaginatedVersionResult(
      pagination: Pagination,
      result: Seq[APIV2.Version]
  )

  @SnakeCaseJsonCodec case class SimplePlatform(
      platform: String,
      platformVersion: Option[String]
  )

  type EditableVersion   = EditableVersionF[Option]
  type DbEditableVersion = DbEditableVersionF[Option]
  case class EditableVersionF[F[_]](
      description: F[Option[String]],
      stability: F[Version.Stability],
      releaseType: F[Option[Version.ReleaseType]],
      platforms: F[List[SimplePlatform]]
  )
  object EditableVersionF {
    implicit val F
        : ApplicativeKC[EditableVersionF] with TraverseKC[EditableVersionF] with DistributiveKC[EditableVersionF] =
      Derive.allKC[EditableVersionF]

    val patchDecoder: EditableVersionF[PatchDecoder] =
      PatchDecoder.fromName(Derive.namesWithProductImplicitsC[EditableVersionF, Decoder])(
        io.circe.derivation.renaming.snakeCase
      )
  }

  case class VersionedPlatformF[F[_]](
      platform: F[List[String]],
      platformVersion: F[List[Option[String]]],
      platformCoarseVersion: F[List[Option[String]]]
  )
  object VersionedPlatformF {
    implicit val F: ApplicativeKC[VersionedPlatformF]
      with TraverseKC[VersionedPlatformF]
      with DistributiveKC[VersionedPlatformF] = Derive.allKC[VersionedPlatformF]
  }

  case class DbEditableVersionF[F[_]](
      description: F[Option[String]],
      stability: F[Version.Stability],
      releaseType: F[Option[Version.ReleaseType]],
      platforms: VersionedPlatformF[F]
  )
  object DbEditableVersionF {
    implicit val F: ApplicativeKC[DbEditableVersionF]
      with TraverseKC[DbEditableVersionF]
      with DistributiveKC[DbEditableVersionF] = Derive.allKC[DbEditableVersionF]
  }

  @SnakeCaseJsonCodec case class ScannedVersion(
      version: APIV2.Version,
      warnings: Option[NonEmptyList[String]]
  )
}
