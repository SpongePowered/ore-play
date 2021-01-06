package controllers.apiv2

import java.nio.file.Path
import java.time.format.DateTimeParseException
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import play.api.http.{HttpErrorHandler, Writeable}
import play.api.i18n.Lang
import play.api.inject.ApplicationLifecycle
import play.api.libs.Files
import play.api.mvc._

import controllers.apiv2.ApiV2Controller._
import controllers.sugar.CircePlayController
import controllers.sugar.Requests.ApiRequest
import controllers.{OreBaseController, OreControllerComponents}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import models.querymodels.{APIV2ProjectStatsQuery, APIV2QueryVersion, APIV2QueryVersionTag, APIV2VersionStatsQuery}
import ore.data.project.Category
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ApiKeyTable, OrganizationTable, ProjectTable, UserTable}
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
import cats.data.{NonEmptyList, Validated}
import cats.kernel.Semigroup
import cats.syntax.all._
import enumeratum._
import io.circe.derivation.annotations.SnakeCaseJsonCodec
import io.circe.syntax._
import io.circe.{Codec => CirceCodec, _}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

class ApiV2Controller(
    factory: ProjectFactory,
    val errorHandler: HttpErrorHandler,
    fakeUser: FakeUser,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends OreBaseController
    with CircePlayController {

  implicit def zioMode[R]: scalacache.Mode[ZIO[R, Throwable, *]] =
    scalacache.CatsEffect.modes.async[ZIO[R, Throwable, *]]

  private val resultCache = scalacache.caffeine.CaffeineCache[ZIO[Blocking, Result, Result]]

  lifecycle.addStopHook(() => zioRuntime.unsafeRunToFuture(resultCache.close[Task]()))

  private def limitOrDefault(limit: Option[Long], default: Long) = math.min(limit.getOrElse(default), default)
  private def offsetOrZero(offset: Long)                         = math.max(offset, 0)

  private def parseAuthHeader(request: Request[_]): IO[Either[Unit, Result], HttpCredentials] = {
    def unAuth[A: Writeable](msg: A) = Unauthorized(msg).withHeaders(WWW_AUTHENTICATE -> "OreApi")

    for {
      stringAuth <- ZIO.fromOption(request.headers.get(AUTHORIZATION)).orElseFail(Left(()))
      parsedAuth = Authorization.parseFromValueString(stringAuth).leftMap { es =>
        NonEmptyList
          .fromList(es)
          .fold(Right(unAuth(ApiError("Could not parse authorization header"))))(es2 =>
            Right(unAuth(ApiErrors(es2.map(_.summary))))
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
      def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> "OreApi")

      val authRequest = for {
        creds <- parseAuthHeader(request)
          .mapError(_.leftMap(_ => unAuth("No authorization specified")).merge)
        token <- ZIO
          .fromOption(creds.params.get("session"))
          .orElseFail(unAuth("No session specified"))
        info <- service
          .runDbCon(APIV2Queries.getApiAuthInfo(token).option)
          .get
          .orElseFail(unAuth("Invalid session"))
        res <- {
          if (info.expires.isBefore(OffsetDateTime.now())) {
            service.deleteWhere(ApiSession)(_.token === token) *> IO.fail(unAuth("Api session expired"))
          } else ZIO.succeed(ApiRequest(info, request))
        }
      } yield res

      zioToFuture(authRequest.either)
    }
  }

  def apiScopeToRealScope(scope: APIScope): IO[Option[Nothing], Scope] = scope match {
    case APIScope.GlobalScope => UIO.succeed(GlobalScope)
    case APIScope.ProjectScope(pluginId) =>
      service
        .runDBIO(
          TableQuery[ProjectTable]
            .filter(_.pluginId === pluginId)
            .map(_.id)
            .result
            .headOption
        )
        .get
        .map(ProjectScope)
    case APIScope.OrganizationScope(organizationName) =>
      val q = for {
        u <- TableQuery[UserTable]
        if u.name === organizationName
        o <- TableQuery[OrganizationTable] if u.id === o.id
      } yield o.id

      service
        .runDBIO(q.result.headOption)
        .get
        .map(OrganizationScope)
  }

  def permApiAction(perms: Permission, scope: APIScope): ActionFilter[ApiRequest] = new ActionFilter[ApiRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApiRequest[A]): Future[Option[Result]] = {
      //Techically we could make this faster by first checking if the global perms have the needed perms,
      //but then we wouldn't get the 404 on a non existent scope.
      val scopePerms: IO[Unit, Permission] =
        apiScopeToRealScope(scope).orElseFail(()).flatMap(request.permissionIn[Scope, IO[Unit, *]](_))
      val res = scopePerms.orElseFail(NotFound).ensure(Forbidden)(_.has(perms))

      zioToFuture(res.either.map(_.swap.toOption))
    }
  }

  def ApiAction(perms: Permission, scope: APIScope): ActionBuilder[ApiRequest, AnyContent] =
    Action.andThen(apiAction).andThen(permApiAction(perms, scope))

  private def expiration(duration: FiniteDuration, userChoice: Option[Long]) = {
    val durationSeconds = duration.toSeconds

    userChoice
      .fold[Option[Long]](Some(durationSeconds))(d => if (d > durationSeconds) None else Some(d))
      .map(OffsetDateTime.now().plusSeconds)
  }

  def authenticateUser(): Action[AnyContent] = Authenticated.asyncF { implicit request =>
    val sessionExpiration = expiration(config.ore.api.session.expiration, None).get //Safe because user choice is None
    val uuidToken         = UUID.randomUUID().toString
    val sessionToInsert   = ApiSession(uuidToken, None, Some(request.user.id), sessionExpiration)

    service.insert(sessionToInsert).map { key =>
      Ok(
        ReturnedApiSession(
          key.token,
          key.expires,
          SessionType.User
        )
      )
    }
  }

  private val uuidRegex = """[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}"""
  private val ApiKeyRegex =
    s"""($uuidRegex).($uuidRegex)""".r

  def authenticateKeyPublic(implicit request: Request[ApiSessionProperties]): ZIO[Any, Result, Result] = {
    lazy val sessionExpiration       = expiration(config.ore.api.session.expiration, request.body.expiresIn)
    lazy val publicSessionExpiration = expiration(config.ore.api.session.publicExpiration, request.body.expiresIn)

    def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> "OreApi")

    val uuidToken = UUID.randomUUID().toString

    val sessionToInsert = parseAuthHeader(request)
      .flatMap { creds =>
        creds.params.get("apikey") match {
          case Some(ApiKeyRegex(identifier, token)) =>
            for {
              expiration <- ZIO
                .succeed(sessionExpiration)
                .get
                .orElseFail(Right(BadRequest("The requested expiration can't be used")))
              t <- service
                .runDbCon(APIV2Queries.findApiKey(identifier, token).option)
                .get
                .orElseFail(Right(unAuth("Invalid api key")))
              (keyId, keyOwnerId) = t
            } yield SessionType.Key -> ApiSession(uuidToken, Some(keyId), Some(keyOwnerId), expiration)
          case _ =>
            ZIO.fail(Right(unAuth("No apikey parameter found in Authorization")))
        }
      }
      .catchAll {
        case Left(_) =>
          ZIO
            .succeed(publicSessionExpiration)
            .get
            .orElseFail(BadRequest("The requested expiration can't be used"))
            .map(expiration => SessionType.Public -> ApiSession(uuidToken, None, None, expiration))
        case Right(e) => ZIO.fail(e)
      }

    sessionToInsert
      .flatMap(t => service.insert(t._2).tupleLeft(t._1))
      .map {
        case (tpe, key) =>
          Ok(
            ReturnedApiSession(
              key.token,
              key.expires,
              tpe
            )
          )
      }
  }

  def authenticateDev: ZIO[Any, Result, Result] = {
    if (fakeUser.isEnabled) {
      config.checkDebug()

      val sessionExpiration = expiration(config.ore.api.session.expiration, None).get //Safe because userChoice is None
      val uuidToken         = UUID.randomUUID().toString
      val sessionToInsert   = ApiSession(uuidToken, None, Some(fakeUser.id), sessionExpiration)

      service.insert(sessionToInsert).map { key =>
        Ok(
          ReturnedApiSession(
            key.token,
            key.expires,
            SessionType.Dev
          )
        )
      }
    } else {
      IO.fail(Forbidden)
    }
  }

  def defaultBody[A](parser: BodyParser[A], default: => A): BodyParser[A] = parse.using { request =>
    if (request.hasBody) parser
    else parse.ignore(default)
  }

  def authenticate(): Action[ApiSessionProperties] =
    Action.asyncF(defaultBody(parseCirce.decodeJson[ApiSessionProperties], ApiSessionProperties(None, None))) {
      implicit request => if (request.body._fake.getOrElse(false)) authenticateDev else authenticateKeyPublic
    }

  def deleteSession(): Action[AnyContent] = ApiAction(Permission.None, APIScope.GlobalScope).asyncF {
    implicit request =>
      ZIO
        .succeed(request.apiInfo.session)
        .get
        .orElseFail(BadRequest("This request was not made with a session"))
        .flatMap(session => service.deleteWhere(ApiSession)(_.token === session))
        .as(NoContent)
  }

  def createKey(): Action[KeyToCreate] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope)(parseCirce.decodeJson[KeyToCreate]).asyncF {
      implicit request =>
        val permsVal = NamedPermission.parseNamed(request.body.permissions).toValidNel("Invalid permission name")
        val nameVal = Some(request.body.name)
          .filter(_.nonEmpty)
          .toValidNel("Name was empty")
          .ensure(NonEmptyList.one("Name too long"))(_.length < 255)

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
          .leftMap((ApiErrors.apply _).andThen(BadRequest.apply(_)).andThen(IO.fail(_)))
          .merge
    }

  def deleteKey(name: String): Action[AnyContent] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope).asyncF { implicit request =>
      for {
        user <- ZIO
          .fromOption(request.user)
          .orElseFail(BadRequest(ApiError("Public keys can't be used to delete")))
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
      scope    <- apiScopeToRealScope(apiScope).orElseFail(NotFound)
      perms    <- request.permissionIn(scope)
    } yield (apiScope, perms)

  def cachingF[A, B](
      cacheKey: String
  )(parts: Any*)(fa: ZIO[Blocking, Result, Result])(implicit request: ApiRequest[B]): ZIO[Blocking, Result, Result] =
    resultCache
      .cachingF[ZIO[Blocking, Throwable, *]](
        cacheKey +: parts :+ request.apiInfo.key.map(_.tokenIdentifier) :+ request.apiInfo.user
          .map(_.id) :+ request.body: _*
      )(Some(1.minute))(fa.memoize)
      .orElseFail(InternalServerError)
      .flatten

  def showPermissions(pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF("showPermissions")(pluginId, organizationName) {
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
    }

  def has(
      cacheKey: String,
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  )(
      check: (Seq[NamedPermission], Permission) => Boolean
  ): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF(cacheKey)(permissions, pluginId, organizationName) {
        permissionsInCreatedApiScope(pluginId, organizationName).map {
          case (scope, perms) =>
            Ok(PermissionCheck(scope.tpe, check(permissions, perms)))
        }
      }
    }

  def hasAll(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has("hasAll", permissions, pluginId, organizationName)((seq, perm) => seq.forall(p => perm.has(p.permission)))

  def hasAny(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has("hasAny", permissions, pluginId, organizationName)((seq, perm) => seq.exists(p => perm.has(p.permission)))

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

  def listVersions(
      pluginId: String,
      tags: Seq[String],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("listVersions")(pluginId, tags, limit, offset) {
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
    }

  def showVersion(pluginId: String, name: String): Action[AnyContent] =
    ApiAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("showVersion")(pluginId, name) {
        service
          .runDbCon(
            APIV2Queries
              .versionQuery(
                pluginId,
                Some(name),
                Nil,
                request.globalPermissions.has(Permission.SeeHidden),
                request.user.map(_.id),
                1,
                0
              )
              .option
          )
          .map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
      }
    }

  def showVersionStats(
      pluginId: String,
      version: String,
      fromDateString: String,
      toDateString: String
  ): Action[AnyContent] =
    ApiAction(Permission.IsProjectMember, APIScope.ProjectScope(pluginId)).asyncF { implicit request =>
      cachingF("versionStats")(pluginId, version, fromDateString, toDateString) {
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
          .ensure("Description too long")(_.description.forall(_.length < Page.maxLength))
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
          user    <- ZIO.fromOption(request.user).orElseFail(BadRequest(ApiError("No user found for session")))
          _       <- uploadErrors(user)
          project <- projects.withPluginId(pluginId).get.orElseFail(NotFound)
          data    <- dataF
          file    <- fileF
          pendingVersion <- factory
            .processSubsequentPluginUpload(PluginUpload(file.ref, file.filename), user, project)
            .leftMap { s =>
              implicit val lang: Lang = user.langOrDefault
              BadRequest(UserError(messagesApi(s)))
            }
            .map { v =>
              v.copy(
                createForumPost = data.createForumPost.getOrElse(project.settings.forumSync),
                channelName =
                  data.tags.getOrElse(Map.empty).view.mapValues(_.first).getOrElse("Channel", v.channelName),
                description = data.description
              )
            }
          t <- pendingVersion.complete(project, factory)
        } yield {
          val (_, version, channel, tags) = t

          val normalApiTags = tags.map(tag => APIV2QueryVersionTag(tag.name, tag.data, tag.color)).toList
          val channelApiTag = APIV2QueryVersionTag(
            "Channel",
            Some(channel.name),
            channel.color.toTagColor
          )
          val apiTags = channelApiTag :: normalApiTags
          val apiVersion = APIV2QueryVersion(
            version.createdAt,
            version.versionString,
            version.dependencyIds,
            version.visibility,
            version.description,
            0,
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
    ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF("showUser")(user) {
        service.runDbCon(APIV2Queries.userQuery(user).option).map(_.fold(NotFound: Result)(a => Ok(a.asJson)))
      }
    }

  def showStarred(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction("showStarred")(
      user,
      sort,
      limit,
      offset,
      APIV2Queries.starredQuery,
      APIV2Queries.starredCountQuery
    )

  def showWatching(
      user: String,
      sort: Option[ProjectSortingStrategy],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    showUserAction("showWatching")(
      user,
      sort,
      limit,
      offset,
      APIV2Queries.watchingQuery,
      APIV2Queries.watchingCountQuery
    )

  def showUserAction(cacheKey: String)(
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
      ) => doobie.Query0[Either[DecodingFailure, APIV2.CompactProject]],
      countQuery: (String, Boolean, Option[DbRef[User]]) => doobie.Query0[Long]
  ): Action[AnyContent] = ApiAction(Permission.ViewPublicInfo, APIScope.GlobalScope).asyncF { implicit request =>
    cachingF(cacheKey)(user, sort, limit, offset) {
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

      (service.runDbCon(getProjects).flatMap(ZIO.foreach(_)(ZIO.fromEither(_))).orDie, service.runDbCon(countProjects))
        .parMapN { (projects, count) =>
          Ok(
            PaginatedCompactProjectResult(
              Pagination(realLimit, offset, count),
              projects
            )
          )
        }
    }
  }
}
object ApiV2Controller {

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

    implicit val codec: CirceCodec[APIScopeType] = APIV2.enumCodec(APIScopeType)(_.entryName)
  }

  sealed abstract class SessionType extends EnumEntry with EnumEntry.Snakecase
  object SessionType extends Enum[SessionType] {
    case object Key    extends SessionType
    case object User   extends SessionType
    case object Public extends SessionType
    case object Dev    extends SessionType

    val values: immutable.IndexedSeq[SessionType] = findValues

    implicit val codec: CirceCodec[SessionType] = APIV2.enumCodec(SessionType)(_.entryName)
  }

  @SnakeCaseJsonCodec case class ApiError(error: String)
  @SnakeCaseJsonCodec case class ApiErrors(errors: NonEmptyList[String])
  object ApiErrors {
    implicit val semigroup: Semigroup[ApiErrors] = (x: ApiErrors, y: ApiErrors) =>
      ApiErrors(x.errors.concatNel(y.errors))
  }
  @SnakeCaseJsonCodec case class UserError(userError: String)

  @SnakeCaseJsonCodec case class KeyToCreate(name: String, permissions: Seq[String])
  @SnakeCaseJsonCodec case class CreatedApiKey(key: String, perms: Seq[NamedPermission])

  @SnakeCaseJsonCodec case class DeployVersionInfo(
      createForumPost: Option[Boolean],
      description: Option[String],
      tags: Option[Map[String, StringOrArrayString]]
  )

  sealed trait StringOrArrayString {
    def first: String
  }
  object StringOrArrayString {
    case class AsString(s: String) extends StringOrArrayString {
      def first: String = s
    }
    case class AsArray(ss: Seq[String]) extends StringOrArrayString {
      override def first: String = ss.head
    }

    implicit val codec: CirceCodec[StringOrArrayString] = CirceCodec.from(
      (c: HCursor) => c.as[String].map(AsString).orElse(c.as[Seq[String]].map(AsArray)), {
        case AsString(s) => s.asJson
        case AsArray(ss) => ss.asJson
      }
    )
  }

  @SnakeCaseJsonCodec case class ApiSessionProperties(
      _fake: Option[Boolean],
      expiresIn: Option[Long]
  )

  @SnakeCaseJsonCodec case class ReturnedApiSession(
      session: String,
      expires: OffsetDateTime,
      `type`: SessionType
  )

  @SnakeCaseJsonCodec case class PaginatedProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.Project]
  )

  @SnakeCaseJsonCodec case class PaginatedCompactProjectResult(
      pagination: Pagination,
      result: Seq[APIV2.CompactProject]
  )

  @SnakeCaseJsonCodec case class PaginatedVersionResult(
      pagination: Pagination,
      result: Seq[APIV2.Version]
  )

  @SnakeCaseJsonCodec case class Pagination(
      limit: Long,
      offset: Long,
      count: Long
  )

  implicit val namedPermissionCodec: CirceCodec[NamedPermission] = APIV2.enumCodec(NamedPermission)(_.entryName)

  @SnakeCaseJsonCodec case class KeyPermissions(
      `type`: APIScopeType,
      permissions: List[NamedPermission]
  )

  @SnakeCaseJsonCodec case class PermissionCheck(
      `type`: APIScopeType,
      result: Boolean
  )
}
