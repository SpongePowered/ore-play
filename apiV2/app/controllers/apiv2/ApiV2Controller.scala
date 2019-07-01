package controllers.apiv2

import scala.language.higherKinds

import java.nio.file.Path
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.{HttpErrorHandler, Writeable}
import play.api.i18n.Lang
import play.api.libs.Files
import play.api.mvc._

import controllers.apiv2.ApiV2Controller._
import controllers.sugar.CircePlayController
import controllers.sugar.Requests.ApiRequest
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
import ore.models.project.io.PluginUpload
import ore.models.project.{Page, ProjectSortingStrategy}
import ore.models.user.{FakeUser, User}
import ore.permission.scope.{GlobalScope, OrganizationScope, ProjectScope, Scope}
import ore.permission.{NamedPermission, Permission}
import _root_.util.syntax._

import akka.http.scaladsl.model.headers.{Authorization, HttpCredentials}
import cats.data.NonEmptyList
import cats.syntax.all._
import enumeratum._
import io.circe._
import io.circe.generic.extras._
import io.circe.syntax._
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

@Singleton
class ApiV2Controller @Inject()(factory: ProjectFactory, val errorHandler: HttpErrorHandler, fakeUser: FakeUser)(
    implicit oreComponents: OreControllerComponents
) extends OreBaseController
    with CircePlayController {

  private def limitOrDefault(limit: Option[Long], default: Long) = math.min(limit.getOrElse(default), default)
  private def offsetOrZero(offset: Long)                         = math.max(offset, 0)

  private def parseAuthHeader(request: Request[_]): IO[Either[Unit, Result], HttpCredentials] = {
    lazy val authUrl                 = routes.ApiV2Controller.authenticate().absoluteURL()(request)
    def unAuth[A: Writeable](msg: A) = Unauthorized(msg).withHeaders(WWW_AUTHENTICATE -> authUrl)

    for {
      stringAuth <- ZIO.fromOption(request.headers.get(AUTHORIZATION)).mapError(Left.apply)
      parsedAuth = Authorization.parseFromValueString(stringAuth).leftMap { es =>
        NonEmptyList
          .fromList(es)
          .fold(Right(unAuth(ApiError("Could not parse authorization header"))))(
            es2 => Right(unAuth(ApiErrors(es2.map(_.summary))))
          )
      }
      auth <- ZIO.fromEither(parsedAuth)
      creds = auth.credentials
      res <- {
        if (creds.scheme == "OreApi")
          ZIO.succeed(creds)
        else
          ZIO.fail(Right(unAuth(ApiError("Invalid scheme for authorization. Needs to be OreApi"))))
      }
    } yield res
  }

  def apiAction: ActionRefiner[Request, ApiRequest] = new ActionRefiner[Request, ApiRequest] {
    def executionContext: ExecutionContext = ec
    override protected def refine[A](request: Request[A]): Future[Either[Result, ApiRequest[A]]] = {
      lazy val authUrl        = routes.ApiV2Controller.authenticate().absoluteURL()(request)
      def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> authUrl)

      val authRequest = for {
        creds <- parseAuthHeader(request)
          .mapError(_.leftMap(_ => unAuth("No authorization specified")).merge)
        token <- ZIO
          .fromOption(creds.params.get("session"))
          .constError(unAuth("No session specified"))
        info <- service
          .runDbCon(APIV2Queries.getApiAuthInfo(token).option)
          .get
          .constError(unAuth("Invalid session"))
        res <- {
          if (info.expires.isBefore(Instant.now())) {
            service.deleteWhere(ApiSession)(_.token === token) *> IO.fail(unAuth("Api session expired"))
          } else ZIO.succeed(ApiRequest(info, request))
        }
      } yield res

      zioToFuture(authRequest.either)
    }
  }

  def apiScopeToRealScope(scope: APIScope): IO[Unit, Scope] = scope match {
    case APIScope.GlobalScope => UIO.succeed(GlobalScope)
    case APIScope.ProjectScope(pluginId) =>
      service
        .runDBIO(
          TableQuery[ProjectTableMain]
            .filter(_.pluginId === pluginId)
            .map(_.id)
            .result
            .headOption
        )
        .get
        .map(ProjectScope)
    case APIScope.OrganizationScope(organizationName) =>
      service
        .runDBIO(
          TableQuery[OrganizationTable]
            .filter(_.name === organizationName)
            .map(_.id)
            .result
            .headOption
        )
        .get
        .map(OrganizationScope)
  }

  def permApiAction(perms: Permission, scope: APIScope): ActionFilter[ApiRequest] = new ActionFilter[ApiRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApiRequest[A]): Future[Option[Result]] = {
      //Techically we could make this faster by first checking if the global perms have the needed perms,
      //but then we wouldn't get the 404 on a non existent scope.
      val scopePerms: IO[Unit, Permission] =
        apiScopeToRealScope(scope).flatMap(request.permissionIn[Scope, IO[Unit, ?]](_))
      val res = scopePerms.constError(NotFound).ensure(Forbidden)(_.has(perms))

      zioToFuture(res.either.map(_.swap.toOption))
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
      action(request).bimap(UIO.succeed, service.runDbCon(_).map(a => Ok(a.asJson))).merge
    }

  def apiEitherVecDbAction[A: Encoder](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[Vector[A]]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(UIO.succeed, service.runDbCon).map(_.map(a => Ok(a.asJson))).merge
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

  def authenticateKeyPublic(): Action[AnyContent] = Action.asyncF { implicit request =>
    lazy val sessionExpiration       = expiration(config.ore.api.session.expiration)
    lazy val publicSessionExpiration = expiration(config.ore.api.session.publicExpiration)

    lazy val authUrl        = routes.ApiV2Controller.authenticate().absoluteURL()(request)
    def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> authUrl)

    val uuidToken = UUID.randomUUID().toString

    val sessionToInsert = parseAuthHeader(request)
      .flatMap { creds =>
        creds.params.get("apikey") match {
          case Some(ApiKeyRegex(identifier, token)) =>
            service
              .runDbCon(APIV2Queries.findApiKey(identifier, token).option)
              .get
              .constError(Right(unAuth("Invalid api key")))
              .map {
                case (keyId, keyOwnerId) =>
                  SessionType.Key -> ApiSession(uuidToken, Some(keyId), Some(keyOwnerId), sessionExpiration)
              }
          case _ =>
            ZIO.fail(Right(unAuth("No apikey parameter found in Authorization")))
        }
      }
      .catchAll {
        case Left(_) =>
          ZIO.succeed(SessionType.Public -> ApiSession(uuidToken, None, None, publicSessionExpiration))
        case Right(e) => ZIO.fail(e)
      }

    sessionToInsert
      .flatMap(t => service.insert(t._2).tupleLeft(t._1))
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
      IO.fail(Forbidden)
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
              IO.fail(BadRequest(ApiError("Not enough permissions to create that key")))
            } else {
              val tokenIdentifier = UUID.randomUUID().toString
              val token           = UUID.randomUUID().toString
              val ownerId         = request.user.get.id.value

              val nameTaken =
                TableQuery[ApiKeyTable].filter(t => t.name === name && t.ownerId === ownerId).exists.result

              val ifTaken = IO.fail(Conflict(ApiError("Name already taken")))
              val ifFree = service
                .runDbCon(APIV2Queries.createApiKey(name, ownerId, tokenIdentifier, token, perm).run)
                .map(_ => Ok(CreatedApiKey(s"$tokenIdentifier.$token", perm.toNamedSeq)))

              (service.runDBIO(nameTaken): IO[Result, Boolean]).ifM(ifTaken, ifFree)
            }
          }
          .leftMap((ApiErrors.apply _).andThen(BadRequest.apply(_)).andThen(IO.fail))
          .merge
    }

  def deleteKey(name: String): Action[AnyContent] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope).asyncF { implicit request =>
      for {
        user <- ZIO
          .fromOption(request.user)
          .constError(BadRequest(ApiError("Public keys can't be used to delete")))
        rowsAffected <- service.runDbCon(APIV2Queries.deleteApiKey(name, user.id.value).run)
      } yield if (rowsAffected == 0) NotFound else NoContent
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
  ): IO[Result, (APIScope, Permission)] =
    for {
      apiScope <- ZIO.fromEither(createApiScope(pluginId, organizationName))
      scope    <- apiScopeToRealScope(apiScope).constError(NotFound)
      perms    <- request.permissionIn(scope)
    } yield (apiScope, perms)

  def showPermissions(pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
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
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
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

  def showProject(pluginId: String): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
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

  //TODO: Do the async part at some point
  private def readFileAsync(file: Path): ZIO[Blocking, Throwable, String] = {
    import zio.blocking._
    effectBlocking(java.nio.file.Files.readAllLines(file).asScala.mkString("\n"))
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
          .ensure("Description too long")(_.description.forall(_.length > Page.maxLength))
          .mapError(e => BadRequest(ApiError(e)))

        val fileF = ZIO.fromEither(
          request.body.file("plugin-file").toRight(BadRequest(ApiError("No plugin file specified")))
        )

        def uploadErrors(user: Model[User]) = {
          implicit val lang: Lang = user.langOrDefault
          ZIO.fromEither(
            factory
              .getUploadError(user)
              .map(e => BadRequest(UserError(messagesApi(e))))
              .toLeft(())
          )
        }

        for {
          user            <- ZIO.fromOption(request.user).constError(BadRequest(ApiError("No user found for session")))
          _               <- uploadErrors(user)
          project         <- projects.withPluginId(pluginId).get.constError(NotFound)
          projectSettings <- project.settings[Task].orDie
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
          t <- pendingVersion.complete(project, factory)
          (project, version, channel, tags) = t
          _ <- {
            if (data.recommended.exists(identity))
              service.update(project)(_.copy(recommendedVersionId = Some(version.id)))
            else IO.unit
          }
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
