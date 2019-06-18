package controllers.apiv2

import scala.language.higherKinds

import java.nio.file.Path
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.{HttpErrorHandler, Writeable}
import play.api.i18n.Lang
import play.api.libs.Files
import play.api.mvc._

import controllers.apiv2.ApiV2Controller._
import controllers.sugar.CircePlayController
import controllers.sugar.Requests.{ApiAuthInfo, ApiRequest}
import controllers.{OreBaseController, OreControllerComponents}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.{APIV2QueryVersion, APIV2QueryVersionTag}
import ore.data.project.Category
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ApiKeyTable, OrganizationTable, ProjectTableMain}
import ore.db.{DbRef, Model}
import ore.models.api.ApiSession
import ore.models.project.factory.ProjectFactory
import ore.models.project.io.{PluginUpload, ProjectFiles}
import ore.models.project.{Page, ProjectSortingStrategy}
import ore.models.user.{FakeUser, User}
import ore.permission.scope.{GlobalScope, OrganizationScope, ProjectScope, Scope}
import ore.permission.{NamedPermission, Permission}
import ore.util.OreMDC
import _root_.util.IOUtils
import _root_.util.syntax._

import akka.http.scaladsl.model.headers.{Authorization, HttpCredentials}
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.effect.{IO, Sync}
import cats.syntax.all._
import com.typesafe.scalalogging
import enumeratum._
import io.circe._
import io.circe.generic.extras._
import io.circe.syntax._

@Singleton
class ApiV2Controller @Inject()(factory: ProjectFactory, val errorHandler: HttpErrorHandler, fakeUser: FakeUser)(
    implicit oreComponents: OreControllerComponents[IO],
    projectFiles: ProjectFiles,
    mat: Materializer
) extends OreBaseController
    with CircePlayController {

  private val Logger = scalalogging.Logger.takingImplicit[OreMDC]("ApiV2")

  private def limitOrDefault(limit: Option[Long], default: Long) = math.min(limit.getOrElse(default), default)
  private def offsetOrZero(offset: Long)                         = math.max(offset, 0)

  private def parseAuthHeader(request: Request[_]): EitherT[IO, Either[Unit, Result], HttpCredentials] = {
    lazy val authUrl                 = routes.ApiV2Controller.authenticate().absoluteURL()(request)
    def unAuth[A: Writeable](msg: A) = Unauthorized(msg).withHeaders(WWW_AUTHENTICATE -> authUrl)

    EitherT
      .fromOption[IO](request.headers.get(AUTHORIZATION), Left(()))
      .map(Authorization.parseFromValueString)
      .map(_.leftMap { es =>
        NonEmptyList
          .fromList(es)
          .fold(Right(unAuth(ApiError("Could not parse authorization header"))))(
            es2 => Right(unAuth(ApiErrors(es2.map(_.summary))))
          )
      })
      .subflatMap(identity)
      .map(_.credentials)
      .subflatMap { creds =>
        if (creds.scheme == "OreApi")
          Right(creds)
        else
          Left(Right(unAuth(ApiError("Invalid scheme for authorization. Needs to be OreApi"))))
      }
  }

  def apiAction: ActionRefiner[Request, ApiRequest] = new ActionRefiner[Request, ApiRequest] {
    def executionContext: ExecutionContext = ec
    override protected def refine[A](request: Request[A]): Future[Either[Result, ApiRequest[A]]] = {
      lazy val authUrl        = routes.ApiV2Controller.authenticate().absoluteURL()(request)
      def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> authUrl)

      parseAuthHeader(request)
        .leftMap(_.leftMap(_ => unAuth("No authorization specified")).merge)
        .flatMap(creds => EitherT.fromOption[IO](creds.params.get("session"), unAuth("No session specified")))
        .flatMap { token =>
          OptionT(service.runDbCon(APIV2Queries.getApiAuthInfo(token).option))
            .toRight(unAuth("Invalid session"))
            .flatMap { info =>
              if (info.expires.isBefore(Instant.now())) {
                EitherT
                  .left[ApiAuthInfo](service.deleteWhere(ApiSession)(_.token === token))
                  .leftMap(_ => unAuth("Api session expired"))
              } else EitherT.rightT[IO, Result](info)
            }
            .map(info => ApiRequest(info, request))
        }
        .value
        .unsafeToFuture()
    }
  }

  def apiScopeToRealScope(scope: APIScope): OptionT[IO, Scope] = scope match {
    case APIScope.GlobalScope => OptionT.pure[IO](GlobalScope)
    case APIScope.ProjectScope(pluginId) =>
      OptionT(
        service.runDBIO(TableQuery[ProjectTableMain].filter(_.pluginId === pluginId).map(_.id).result.headOption)
      ).map(id => ProjectScope(id))
    case APIScope.OrganizationScope(organizationName) =>
      OptionT(
        service.runDBIO(
          TableQuery[OrganizationTable].filter(_.name === organizationName).map(_.id).result.headOption
        )
      ).map(id => OrganizationScope(id))
  }

  def permApiAction(perms: Permission, scope: APIScope): ActionFilter[ApiRequest] = new ActionFilter[ApiRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApiRequest[A]): Future[Option[Result]] = {
      //Techically we could make this faster by first checking if the global perms have the needed perms,
      //but then we wouldn't get the 404 on a non existent scope.
      val scopePerms = apiScopeToRealScope(scope).semiflatMap(request.permissionIn(_))

      scopePerms.toRight(NotFound).ensure(Forbidden)(_.has(perms)).swap.toOption.value.unsafeToFuture()
    }
  }

  def ApiAction(perms: Permission, scope: APIScope): ActionBuilder[ApiRequest, AnyContent] =
    Action.andThen(apiAction).andThen(permApiAction(perms, scope))

  def apiDbAction[A: Encoder](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[A]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(a => Ok(a.asJson))
    }

  def apiOptDbAction[A: Encoder](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[Option[A]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
    }

  def apiEitherDbAction[A: Encoder](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[A]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon(_).map(a => Ok(a.asJson))).merge
    }

  def apiEitherVecDbAction[A: Encoder](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[Vector[A]]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon).map(_.map(a => Ok(a.asJson))).merge
    }

  def apiVecDbAction[A: Encoder](
      perms: Permission,
      scope: APIScope
  )(action: ApiRequest[AnyContent] => doobie.ConnectionIO[Vector[A]]): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(xs => Ok(xs.asJson))
    }

  private def expiration(duration: FiniteDuration) = Instant.now().plusSeconds(duration.toSeconds)

  def authenticateUser(): Action[AnyContent] = Authenticated.asyncF { implicit request =>
    val sessionExpiration = expiration(config.ore.api.session.expiration)
    val uuidToken         = UUID.randomUUID().toString
    val sessionToInsert   = ApiSession(uuidToken, None, Some(request.user.id), sessionExpiration)

    service.insert(sessionToInsert).map { key =>
      Ok(
        ReturnedApiSession(
          key.token,
          LocalDateTime.ofInstant(key.expires, ZoneOffset.UTC),
          SessionType.User
        )
      )
    }
  }

  private val uuidRegex = """[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}"""
  private val ApiKeyRegex =
    s"""($uuidRegex).($uuidRegex)""".r

  def authenticateKeyPublic(): Action[AnyContent] = Action.asyncEitherT { implicit request =>
    lazy val sessionExpiration       = expiration(config.ore.api.session.expiration)
    lazy val publicSessionExpiration = expiration(config.ore.api.session.publicExpiration)

    lazy val authUrl        = routes.ApiV2Controller.authenticate().absoluteURL()(request)
    def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> authUrl)

    val uuidToken = UUID.randomUUID().toString

    val sessionToInsert2 = parseAuthHeader(request)
      .flatMap[Either[Unit, Result], (SessionType, ApiSession)] { creds =>
        creds.params.get("apikey") match {
          case Some(ApiKeyRegex(identifier, token)) =>
            OptionT(service.runDbCon(APIV2Queries.findApiKey(identifier, token).option))
              .toRight(Right(unAuth("Invalid api key")): Either[Unit, Result])
              .map {
                case (keyId, keyOwnerId) =>
                  SessionType.Key -> ApiSession(uuidToken, Some(keyId), Some(keyOwnerId), sessionExpiration)
              }
          case _ =>
            EitherT.leftT[IO, (SessionType, ApiSession)](
              Right(unAuth("No apikey parameter found in Authorization")): Either[Unit, Result]
            )
        }
      }
      .leftFlatMap[(SessionType, ApiSession), Result] {
        case Left(_) =>
          EitherT.rightT[IO, Result](SessionType.Public -> ApiSession(uuidToken, None, None, publicSessionExpiration))
        case Right(e) => EitherT.leftT[IO, (SessionType, ApiSession)](e)
      }

    sessionToInsert2
      .semiflatMap(t => service.insert(t._2).tupleLeft(t._1))
      .map {
        case (tpe, key) =>
          Ok(
            ReturnedApiSession(
              key.token,
              LocalDateTime.ofInstant(key.expires, ZoneOffset.UTC),
              tpe
            )
          )
      }
  }

  def authenticateDev(): Action[AnyContent] = Action.asyncF {
    if (fakeUser.isEnabled) {
      config.checkDebug()

      val sessionExpiration = expiration(config.ore.api.session.expiration)
      val uuidToken         = UUID.randomUUID().toString
      val sessionToInsert   = ApiSession(uuidToken, None, Some(fakeUser.id), sessionExpiration)

      service.insert(sessionToInsert).map { key =>
        Ok(
          ReturnedApiSession(
            key.token,
            LocalDateTime.ofInstant(key.expires, ZoneOffset.UTC),
            SessionType.Dev
          )
        )
      }
    } else {
      IO.pure(Forbidden)
    }
  }

  def authenticate(fake: Boolean): Action[AnyContent] = if (fake) authenticateDev() else authenticateKeyPublic()

  def createKey(): Action[KeyToCreate] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope)(parseCirce.decodeJson[KeyToCreate]).asyncF {
      implicit request =>
        val permsVal = NamedPermission.parseNamed(request.body.permissions).toValidNel("Invalid permission name")
        val nameVal  = Some(request.body.name).filter(_.nonEmpty).toValidNel("Name was empty")

        (permsVal, nameVal)
          .mapN { (perms, name) =>
            val perm     = Permission(perms.map(_.permission): _*)
            val isSubKey = request.apiInfo.key.forall(_.isSubKey(perm))

            if (!isSubKey) {
              IO.pure(BadRequest(ApiError("Not enough permissions to create that key")))
            } else {
              val tokenIdentifier = UUID.randomUUID().toString
              val token           = UUID.randomUUID().toString
              val ownerId         = request.user.get.id.value

              val nameTaken =
                TableQuery[ApiKeyTable].filter(t => t.name === name && t.ownerId === ownerId).exists.result

              val ifTaken = IO.pure(Conflict(ApiError("Name already taken")))
              val ifFree = service
                .runDbCon(APIV2Queries.createApiKey(name, ownerId, tokenIdentifier, token, perm).run)
                .map(_ => Ok(CreatedApiKey(s"$tokenIdentifier.$token", perm.toNamedSeq)))

              service.runDBIO(nameTaken).ifM(ifTaken, ifFree)
            }
          }
          .leftMap((ApiErrors.apply _).andThen(BadRequest.apply(_)).andThen(IO.pure(_)))
          .merge
    }

  def deleteKey(name: String): Action[AnyContent] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope).asyncEitherT { implicit request =>
      EitherT
        .fromOption[IO](request.user, BadRequest(ApiError("Public keys can't be used to delete")))
        .semiflatMap { user =>
          service.runDbCon(APIV2Queries.deleteApiKey(name, user.id.value).run).map {
            case 0 => NotFound: Result
            case _ => NoContent: Result
          }
        }
    }

  def createApiScope(pluginId: Option[String], organizationName: Option[String]): Either[Result, APIScope] =
    (pluginId, organizationName) match {
      case (Some(_), Some(_)) =>
        Left(BadRequest(ApiError("Can't check for project and organization permissions at the same time")))
      case (Some(plugId), None)  => Right(APIScope.ProjectScope(plugId))
      case (None, Some(orgName)) => Right(APIScope.OrganizationScope(orgName))
      case (None, None)          => Right(APIScope.GlobalScope)
    }

  def permissionsInCreatedApiScope(pluginId: Option[String], organizationName: Option[String])(
      implicit request: ApiRequest[_]
  ): EitherT[IO, Result, (APIScope, Permission)] =
    EitherT
      .fromEither[IO](createApiScope(pluginId, organizationName))
      .flatMap(t => apiScopeToRealScope(t).tupleLeft(t).toRight(NotFound: Result))
      .semiflatMap(t => request.permissionIn(t._2).tupleLeft(t._1))

  def showPermissions(pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncEitherT { implicit request =>
      permissionsInCreatedApiScope(pluginId, organizationName).map {
        case (scope, perms) =>
          Ok(
            KeyPermissions(
              scope.tpe,
              perms.toNamedSeq.toList
            )
          )
      }
    }

  def has(permissions: Seq[NamedPermission], pluginId: Option[String], organizationName: Option[String])(
      check: (Seq[NamedPermission], Permission) => Boolean
  ): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncEitherT { implicit request =>
      permissionsInCreatedApiScope(pluginId, organizationName).map {
        case (scope, perms) =>
          Ok(PermissionCheck(scope.tpe, check(permissions, perms)))
      }
    }

  def hasAll(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has(permissions, pluginId, organizationName)((seq, perm) => seq.forall(p => perm.has(p.permission)))

  def hasAny(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has(permissions, pluginId, organizationName)((seq, perm) => seq.exists(p => perm.has(p.permission)))

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
      val realLimit  = limitOrDefault(limit, config.ore.projects.initLoad)
      val realOffset = offsetOrZero(offset)
      val getProjects = APIV2Queries
        .projectQuery(
          None,
          categories.toList,
          tags.toList,
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
          tags.toList,
          q,
          owner,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id)
        )
        .unique

      (service.runDbCon(getProjects), service.runDbCon(countProjects)).parMapN { (projects, count) =>
        Ok(
          PaginatedProjectResult(
            Pagination(realLimit, realOffset, count),
            projects
          )
        )
      }
    }

  def showProject(pluginId: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { implicit request =>
      APIV2Queries
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
    }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    apiVecDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries
        .projectMembers(pluginId, limitOrDefault(limit, 25), offsetOrZero(offset))
        .to[Vector]
    }

  def listVersions(
      pluginId: String,
      tags: Seq[String],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF {
      val realLimit  = limitOrDefault(limit, config.ore.projects.initVersionLoad.toLong)
      val realOffset = offsetOrZero(offset)
      val getVersions = APIV2Queries
        .versionQuery(
          pluginId,
          None,
          tags.toList,
          realLimit,
          realOffset
        )
        .to[Vector]

      val countVersions = APIV2Queries.versionCountQuery(pluginId, tags.toList).unique

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
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries.versionQuery(pluginId, Some(name), Nil, 1, 0).option
    }

  //Not sure if FileIO us AsynchronousFileChannel, if it doesn't we can fix this later if it becomes a problem
  private def readFileAsync(file: Path): IO[String] =
    IO.fromFuture(IO(FileIO.fromPath(file).fold(ByteString.empty)(_ ++ _).map(_.utf8String).runFold("")(_ + _)))

  def deployVersion(pluginId: String): Action[MultipartFormData[Files.TemporaryFile]] =
    ApiAction(Permission.CreateVersion, APIScope.ProjectScope(pluginId))(parse.multipartFormData).asyncEitherT {
      implicit request =>
        type TempFile = MultipartFormData.FilePart[Files.TemporaryFile]

        val acquire = OptionT(IO(request.body.file("plugin-info")))
        val use     = (filePart: TempFile) => OptionT.liftF(readFileAsync(filePart.ref))
        val release = (filePart: TempFile) =>
          OptionT.liftF(
            IO(java.nio.file.Files.deleteIfExists(filePart.ref))
              .runAsync(IOUtils.logCallback("Error deleting file upload", Logger))
              .toIO
        )

        val pluginInfoFromFileF = Sync.catsOptionTSync[IO].bracket(acquire)(use)(release)

        val fileF = EitherT.fromEither[IO](
          request.body.file("plugin-file").toRight(BadRequest(ApiError("No plugin file specified")))
        )
        val dataF = OptionT
          .fromOption[IO](request.body.dataParts.get("plugin-info").flatMap(_.headOption))
          .orElse(pluginInfoFromFileF)
          .toRight("No or invalid plugin info specified")
          .subflatMap(s => parser.decode[DeployVersionInfo](s).leftMap(_.show))
          .ensure("Description too long")(_.description.forall(_.length > Page.maxLength))
          .leftMap(e => BadRequest(ApiError(e)))

        def uploadErrors(user: Model[User]) = {
          implicit val lang: Lang = user.langOrDefault
          EitherT.fromEither[IO](
            factory
              .getUploadError(user)
              .map(e => BadRequest(UserError(messagesApi(e))))
              .toLeft(())
          )
        }

        for {
          user            <- EitherT.fromOption[IO](request.user, BadRequest(ApiError("No user found for session")))
          _               <- uploadErrors(user)
          project         <- projects.withPluginId(pluginId).toRight(NotFound: Result)
          projectSettings <- EitherT.right[Result](project.settings)
          data            <- dataF
          file            <- fileF
          pendingVersion <- factory
            .processSubsequentPluginUpload(PluginUpload(file.ref, file.filename), user, project)
            .leftMap { s =>
              implicit val lang: Lang = user.langOrDefault
              BadRequest(UserError(messagesApi(s)))
            }
            .map { v =>
              v.copy(
                createForumPost = data.create_forum_post.getOrElse(projectSettings.forumSync),
                channelName = data.tags.getOrElse("Channel", v.channelName),
                description = data.description
              )
            }
          t <- EitherT.right[Result](pendingVersion.complete(project, factory))
          (project, version, channel, tags) = t
          _ <- EitherT.right[Result](
            if (data.recommended.exists(identity))
              service.update(project)(_.copy(recommendedVersionId = Some(version.id)))
            else IO.unit
          )
        } yield {
          val normalApiTags = tags.map(tag => APIV2QueryVersionTag(tag.name, tag.data, tag.color)).toList
          val channelApiTag = APIV2QueryVersionTag(
            "Channel",
            channel.name,
            channel.color.toTagColor
          )
          val apiTags = channelApiTag :: normalApiTags
          val apiVersion = APIV2QueryVersion(
            LocalDateTime.ofInstant(version.createdAt, ZoneOffset.UTC),
            version.versionString,
            version.dependencyIds,
            version.visibility,
            version.description,
            version.downloadCount,
            version.fileSize,
            version.hash,
            version.fileName,
            Some(user.name),
            version.reviewState,
            apiTags
          )

          Created(apiVersion.asProtocol)
        }
    }

  def showUser(user: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.GlobalScope)(_ => APIV2Queries.userQuery(user).option)

  def showStarred(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction(user, sort, limit, offset, APIV2Queries.starredQuery, APIV2Queries.starredCountQuery)

  def showWatching(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction(user, sort, limit, offset, APIV2Queries.watchingQuery, APIV2Queries.watchingCountQuery)

  def showUserAction(
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
      ) => doobie.Query0[APIV2.CompactProject],
      countQuery: (String, Boolean, Option[DbRef[User]]) => doobie.Query0[Long]
  ): Action[AnyContent] = ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { request =>
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

    (service.runDbCon(getProjects), service.runDbCon(countProjects)).parMapN { (projects, count) =>
      Ok(
        PaginatedCompactProjectResult(
          Pagination(realLimit, offset, count),
          projects
        )
      )
    }
  }
}
object ApiV2Controller {

  import APIV2.config

  sealed abstract class APIScope(val tpe: APIScopeType)
  object APIScope {
    case object GlobalScope                                extends APIScope(APIScopeType.Global)
    case class ProjectScope(pluginId: String)              extends APIScope(APIScopeType.Project)
    case class OrganizationScope(organizationName: String) extends APIScope(APIScopeType.Organization)
  }

  sealed abstract class APIScopeType extends EnumEntry with EnumEntry.Snakecase
  object APIScopeType extends Enum[APIScopeType] {
    case object Global       extends APIScopeType
    case object Project      extends APIScopeType
    case object Organization extends APIScopeType

    val values: immutable.IndexedSeq[APIScopeType] = findValues

    implicit val encoder: Encoder[APIScopeType] = APIV2.enumEncoder(APIScopeType)(_.entryName)
    implicit val decoder: Decoder[APIScopeType] = APIV2.enumDecoder(APIScopeType)(_.entryName)
  }

  sealed abstract class SessionType extends EnumEntry with EnumEntry.Snakecase
  object SessionType extends Enum[SessionType] {
    case object Key    extends SessionType
    case object User   extends SessionType
    case object Public extends SessionType
    case object Dev    extends SessionType

    val values: immutable.IndexedSeq[SessionType] = findValues

    implicit val encoder: Encoder[SessionType] = APIV2.enumEncoder(SessionType)(_.entryName)
    implicit val decoder: Decoder[SessionType] = APIV2.enumDecoder(SessionType)(_.entryName)
  }

  @ConfiguredJsonCodec case class ApiError(error: String)
  @ConfiguredJsonCodec case class ApiErrors(errors: NonEmptyList[String])
  @ConfiguredJsonCodec case class UserError(user_error: String)

  @ConfiguredJsonCodec case class KeyToCreate(name: String, permissions: Seq[String])
  @ConfiguredJsonCodec case class CreatedApiKey(key: String, perms: Seq[NamedPermission])

  @ConfiguredJsonCodec case class DeployVersionInfo(
      recommended: Option[Boolean],
      create_forum_post: Option[Boolean],
      description: Option[String],
      tags: Map[String, String]
  )

  @ConfiguredJsonCodec case class ReturnedApiSession(
      session: String,
      expires: LocalDateTime,
      @JsonKey("type") tpe: SessionType
  )

  @ConfiguredJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  @ConfiguredJsonCodec case class PaginatedCompactProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.CompactProject]
  )

  @ConfiguredJsonCodec case class PaginatedVersionResult(
      pagination: Pagination,
      result: Seq[APIV2.Version]
  )

  @ConfiguredJsonCodec case class Pagination(
      limit: Long,
      offset: Long,
      count: Long
  )

  implicit val namedPermissionEncoder: Encoder[NamedPermission] = APIV2.enumEncoder(NamedPermission)(_.entryName)
  implicit val namedPermissionDecoder: Decoder[NamedPermission] = APIV2.enumDecoder(NamedPermission)(_.entryName)

  @ConfiguredJsonCodec case class KeyPermissions(
      @JsonKey("type") tpe: APIScopeType,
      permissions: List[NamedPermission]
  )

  @ConfiguredJsonCodec case class PermissionCheck(
      @JsonKey("type") tpe: APIScopeType,
      result: Boolean
  )
}
