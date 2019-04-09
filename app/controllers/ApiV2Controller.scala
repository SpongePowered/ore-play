package controllers

import scala.language.higherKinds

import java.nio.file.Path
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.Inject

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.cache.AsyncCacheApi
import play.api.libs.Files
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json._
import play.api.mvc._

import controllers.ApiV2Controller.{APIScope, CreatedKey, DeleteKey, DeployVersionInfo}
import controllers.sugar.Bakery
import controllers.sugar.Requests.{ApiAuthInfo, ApiRequest}
import db.{Model, ModelService}
import db.access.ModelView
import db.query.{APIV2Queries, UserQueries}
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationTable, ProjectTableMain}
import models.api.{ApiKey, ApiSession}
import models.project.{Page, Version}
import models.querymodels.{APIV2Project, APIV2Version, APIV2VersionTag}
import models.user.User
import ore.permission.{NamedPermission, Permission}
import ore.permission.scope.{GlobalScope, OrganizationScope, ProjectScope, Scope}
import ore.project.factory.ProjectFactory
import ore.project.io.PluginUpload
import ore.project.{Category, ProjectSortingStrategy}
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import _root_.util.{IOUtils, OreMDC}

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import cats.Traverse
import cats.data.{EitherT, OptionT}
import cats.effect.{IO, Sync}
import cats.instances.list._
import cats.instances.option._
import cats.syntax.all._
import com.typesafe.scalalogging

class ApiV2Controller @Inject()(factory: ProjectFactory)(
    implicit val ec: ExecutionContext,
    env: OreEnv,
    config: OreConfig,
    service: ModelService,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    cache: AsyncCacheApi,
    mat: Materializer
) extends OreBaseController {

  private val Logger = scalalogging.Logger.takingImplicit[OreMDC]("ApiV2")

  private def limitOrDefault(limit: Option[Long], default: Long) = math.min(limit.getOrElse(default), default)

  private def parseOpt[F[_]: Traverse, A](opt: F[String], parse: String => Option[A], errorMsg: => String) =
    opt.traverse(parse(_)).toRight(BadRequest(errorMsg))

  def apiAction: ActionRefiner[Request, ApiRequest] = new ActionRefiner[Request, ApiRequest] {
    def executionContext: ExecutionContext = ec
    override protected def refine[A](request: Request[A]): Future[Either[Result, ApiRequest[A]]] = {
      val optToken = request.headers
        .get(AUTHORIZATION)
        .map(_.split(" ", 2))
        .filter(_.length == 2)
        .map(arr => arr.head -> arr(1))
        .collect { case ("ApiSession", session) => session }

      lazy val authUrl         = routes.ApiV2Controller.authenticate().absoluteURL()(request)
      def unAuth(msg: JsValue) = Unauthorized(msg).withHeaders(WWW_AUTHENTICATE -> authUrl)

      optToken
        .fold(EitherT.leftT[IO, ApiRequest[A]](unAuth(Json.obj("error" -> "No session specified")))) { token =>
          OptionT(service.runDbCon(UserQueries.getApiAuthInfo(token).option))
            .toRight(unAuth(Json.obj("error" -> "Invalid session")))
            .flatMap { info =>
              if (info.expires.isBefore(Instant.now())) {
                EitherT
                  .left[ApiAuthInfo](service.deleteWhere(ApiSession)(_.token === token))
                  .leftMap(_ => unAuth(Json.obj("error" -> "Api session expired")))
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

  def apiDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[A]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(a => Ok(Json.toJson(a)))
    }

  def apiOptDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[Option[A]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(_.fold(NotFound: Result)(a => Ok(Json.toJson(a))))
    }

  def apiEitherDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[A]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon(_).map(a => Ok(Json.toJson(a)))).merge
    }

  def apiEitherVecDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[Vector[A]]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon).map(_.map(a => Ok(Json.toJson(a)))).merge
    }

  def apiVecDbAction[A: Writes](
      perms: Permission,
      scope: APIScope
  )(action: ApiRequest[AnyContent] => doobie.ConnectionIO[Vector[A]]): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(xs => Ok(Json.toJson(xs)))
    }

  private def expiration(duration: FiniteDuration) = service.theTime.toInstant.plusSeconds(duration.toSeconds)

  def authenticateUser(): Action[AnyContent] = Authenticated.asyncF { implicit request =>
    val sessionExpiration = expiration(config.ore.api.session.expiration)
    val uuidToken         = UUID.randomUUID().toString
    val tpe               = "user"
    val sessionToInsert   = ApiSession(uuidToken, None, Some(request.user.id), sessionExpiration)

    service.insert(sessionToInsert).map { key =>
      Ok(
        Json.obj(
          "session" -> key.token,
          "expires" -> LocalDateTime.ofInstant(key.expires, ZoneOffset.UTC),
          "type"    -> tpe
        )
      )
    }
  }

  def authenticate(): Action[AnyContent] = OreAction.asyncEitherT { implicit request =>
    lazy val sessionExpiration       = expiration(config.ore.api.session.expiration)
    lazy val publicSessionExpiration = expiration(config.ore.api.session.publicExpiration)

    val optApiKey = request.headers
      .get(AUTHORIZATION)
      .map(_.split(" ", 2))
      .filter(_.length == 2)
      .map(arr => arr.head -> arr(1))
      .collect { case ("ApiKey", key) => key }

    val uuidToken = UUID.randomUUID().toString

    val sessionToInsert = optApiKey match {
      case Some(key) =>
        ModelView.now(ApiKey).find(_.token === key).map { key =>
          "key" -> ApiSession(uuidToken, Some(key.id), Some(key.ownerId), sessionExpiration)
        }
      case None =>
        OptionT.pure[IO]("public" -> ApiSession(uuidToken, None, None, publicSessionExpiration))
    }

    sessionToInsert
      .semiflatMap(t => service.insert(t._2).tupleLeft(t._1))
      .map {
        case (tpe, key) =>
          Ok(
            Json.obj(
              "session" -> key.token,
              "expires" -> LocalDateTime.ofInstant(key.expires, ZoneOffset.UTC),
              "type"    -> tpe
            )
          )
      }
      .toRight(
        Unauthorized(Json.obj("error" -> "Invalid api key"))
          .withHeaders(WWW_AUTHENTICATE -> routes.ApiV2Controller.authenticate().absoluteURL())
      )
  }

  def createKey(): Action[CreatedKey] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope)(parse.json[CreatedKey]).asyncF { implicit request =>
      NamedPermission
        .parseNamed(request.body.permissions)
        .fold(IO.pure(BadRequest(Json.obj("error" -> "Invalid permission name")))) { perms =>
          val perm = Permission(perms.map(_.permission): _*)
          service
            .insert(ApiKey(request.body.name.filter(_.nonEmpty), request.user.get.id, UUID.randomUUID().toString, perm))
            .map(key => Ok(Json.obj("key" -> key.token)))
        }
    }

  def deleteKey(): Action[DeleteKey] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope)(parse.json[DeleteKey]).asyncEitherT { implicit request =>
      EitherT
        .fromOption[IO](request.user, BadRequest(Json.obj("error" -> "Public keys can't be used to delete")))
        .flatMap { user =>
          ModelView
            .now(ApiKey)
            .find(k => k.token === request.body.key && k.ownerId === user.id.value)
            .toRight(NotFound: Result)
        }
        .semiflatMap(service.delete(_))
        .as(NoContent)
    }

  def createApiScope(pluginId: Option[String], organizationName: Option[String]): Either[Result, (String, APIScope)] =
    (pluginId, organizationName) match {
      case (Some(_), Some(_)) =>
        Left(BadRequest(Json.obj("error" -> "Can't check for project and organization permissions at the same time")))
      case (Some(plugId), None)  => Right("project"      -> APIScope.ProjectScope(plugId))
      case (None, Some(orgName)) => Right("organization" -> APIScope.OrganizationScope(orgName))
      case (None, None)          => Right("global"       -> APIScope.GlobalScope)
    }

  def permissionsInCreatedApiScope(pluginId: Option[String], organizationName: Option[String])(
      implicit request: ApiRequest[_]
  ): EitherT[IO, Result, (String, Permission)] =
    EitherT
      .fromEither[IO](createApiScope(pluginId, organizationName))
      .flatMap(t => apiScopeToRealScope(t._2).tupleLeft(t._1).toRight(NotFound: Result))
      .semiflatMap(t => request.permissionIn(t._2).tupleLeft(t._1))

  def showPermissions(pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncEitherT { implicit request =>
      permissionsInCreatedApiScope(pluginId, organizationName).map {
        case (tpe, perms) =>
          Ok(
            Json.obj(
              "type"        -> tpe,
              "permissions" -> JsArray(perms.toNamedSeq.map(p => JsString(p.entryName)))
            )
          )
      }
    }

  def has(permissions: Seq[String], pluginId: Option[String], organizationName: Option[String])(
      check: (Seq[NamedPermission], Permission) => Boolean
  ): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncEitherT { implicit request =>
      NamedPermission
        .parseNamed(permissions)
        .fold(EitherT.leftT[IO, Result](BadRequest(Json.obj("error" -> "Invalid permission name")))) { namedPerms =>
          permissionsInCreatedApiScope(pluginId, organizationName).map {
            case (tpe, perms) =>
              Ok(Json.obj("type" -> tpe, "check" -> check(namedPerms, perms)))
          }
        }
    }

  def hasAll(permissions: Seq[String], pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    has(permissions, pluginId, organizationName)((seq, perm) => seq.forall(p => perm.has(p.permission)))

  def hasAny(permissions: Seq[String], pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    has(permissions, pluginId, organizationName)((seq, perm) => seq.exists(p => perm.has(p.permission)))

  def listProjects(
      q: Option[String],
      categories: Seq[String],
      tags: Seq[String],
      owner: Option[String],
      sort: Option[String],
      relevance: Option[Boolean],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    apiEitherVecDbAction[APIV2Project](Permission.ViewPublicInfo, APIScope.GlobalScope) { request =>
      for {
        cats      <- parseOpt(categories.toList, Category.fromApiName, "Unknown category")
        sortStrat <- parseOpt(sort, ProjectSortingStrategy.fromApiName, "Unknown sort strategy")
      } yield {
        APIV2Queries
          .projectQuery(
            None,
            cats,
            tags.toList.flatMap(_.split(",").toList),
            q,
            owner,
            request.globalPermissions.has(Permission.SeeHidden),
            request.user.map(_.id),
            sortStrat.getOrElse(ProjectSortingStrategy.Default),
            relevance.getOrElse(true),
            limitOrDefault(limit, config.ore.projects.initLoad),
            offset
          )
          .to[Vector]
      }
    }

  def showProject(pluginId: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { request =>
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

  def countProjects(
      q: Option[String],
      categories: Seq[String],
      tags: Seq[String],
      owner: Option[String],
  ): Action[AnyContent] = apiEitherDbAction(Permission.ViewPublicInfo, APIScope.GlobalScope) { request =>
    for {
      cats <- parseOpt(categories.toList, Category.fromApiName, "Unknown category")
    } yield {
      APIV2Queries
        .projectCountQuery(
          None,
          cats,
          tags.toList.flatMap(_.split(",").toList),
          q,
          owner,
          request.globalPermissions.has(Permission.SeeHidden),
          request.user.map(_.id)
        )
        .unique
        .map { count =>
          Json.obj("count" -> count)
        }
    }
  }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    apiVecDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries
        .projectMembers(pluginId, limitOrDefault(limit, 25), offset)
        .map(mem => mem.copy(roles = mem.roles.sortBy(_.permissions: Long)))
        .to[Vector]
    }

  def listVersions(
      pluginId: String,
      tags: Seq[String],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    apiVecDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries
        .versionQuery(
          pluginId,
          None,
          tags.toList,
          limitOrDefault(limit, config.ore.projects.initVersionLoad.toLong),
          offset
        )
        .to[Vector]
    }

  def countVersions(
      pluginId: String,
      tags: Seq[String],
  ): Action[AnyContent] =
    apiDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries.versionCountQuery(pluginId, tags.toList).unique.map { count =>
        Json.obj("count" -> count)
      }
    }

  def showVersion(pluginId: String, name: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries.versionQuery(pluginId, Some(name), Nil, 1, 0).option
    }

  //Not sure if FileIO us AsynchronousFileChannel, if it doesn't we can fix this later if it becomes a problem
  private def readFileAsync(file: Path): IO[String] =
    IO.fromFuture(IO(FileIO.fromPath(file).fold(ByteString.empty)(_ ++ _).map(_.utf8String).runFold("")(_ + _)))

  //TODO: Currently won't work until we deal with PGP stuff
  def deployVersion(pluginId: String, versionName: String): Action[MultipartFormData[Files.TemporaryFile]] =
    ApiAction(Permission.CreateVersion, APIScope.ProjectScope(pluginId))(parse.multipartFormData).asyncEitherT {
      implicit request =>
        type TempFile = MultipartFormData.FilePart[Files.TemporaryFile]
        val checkAlreadyExists = EitherT(
          ModelView
            .now(Version)
            .exists(_.versionString === versionName)
            .ifM(IO.pure(Left(Conflict(Json.obj("error" -> "Version already exists")))), IO.pure(Right(())))
        )

        val acquire = OptionT(IO(request.body.file("plugin-info")))
        val use     = (filePart: TempFile) => OptionT.liftF(readFileAsync(filePart.ref))
        val release = (filePart: TempFile) =>
          OptionT.liftF(
            IO(java.nio.file.Files.deleteIfExists(filePart.ref))
              .runAsync(IOUtils.logCallback("Error deleting file upload", Logger))
              .toIO
        )

        val pluginInfoFromFileF = Sync.catsOptionTSync[IO].bracket(acquire)(use)(release)

        val fileF = EitherT
          .fromEither[IO](request.body.file("plugin-file").toRight(JsString("No plugin file specified")))
          .leftMap(js => BadRequest(Json.obj("error" -> js)))
        val dataF = OptionT
          .fromOption[IO](request.body.dataParts.get("plugin-info").flatMap(_.headOption))
          .orElse(pluginInfoFromFileF)
          .toRight(JsString("No or invalid plugin info specified"))
          .subflatMap(s => Try(Json.parse(s)).toEither.leftMap(_ => JsString("Invalid json string")))
          .subflatMap(_.validate[DeployVersionInfo].asEither.leftMap(JsError.toJson))
          .ensure(JsString("Description too short"))(_.description.forall(_.length < Page.minLength))
          .ensure(JsString("Description too long"))(_.description.forall(_.length > Page.maxLength))
          .leftMap(js => BadRequest(Json.obj("error" -> js)))

        def uploadErrors(user: Model[User]) = {
          import user.obj.langOrDefault
          EitherT.fromEither[IO](
            factory
              .getUploadError(user)
              .map(e => BadRequest(Json.obj("user_error" -> messagesApi(e))))
              .toLeft(())
          )
        }

        for {
          user            <- EitherT.fromOption[IO](request.user, BadRequest(Json.obj("error" -> "No user found for session")))
          _               <- checkAlreadyExists
          _               <- uploadErrors(user)
          project         <- projects.withPluginId(pluginId).toRight(NotFound: Result)
          projectSettings <- EitherT.right[Result](project.settings)
          data            <- dataF
          file            <- fileF
          pendingVersion <- factory
            .processSubsequentPluginUpload(PluginUpload(file.ref, file.filename, ???, ???), user, project)
            .leftMap(s => BadRequest(Json.obj("user_error" -> s)))
            .map { v =>
              v.copy(
                createForumPost = data.createForumPost.getOrElse(projectSettings.forumSync),
                channelName = data.tags.getOrElse("Channel", v.channelName),
                description = data.description
              )
            }
          t <- EitherT.right[Result](pendingVersion.complete(project, factory))
          (version, channel, tags) = t
        } yield {
          val normalApiTags = tags.map(tag => APIV2VersionTag(tag.name, tag.data, tag.color)).toList
          val channelApiTag = APIV2VersionTag(
            "Channel",
            channel.name,
            channel.color.toTagColor
          )
          val apiTags = channelApiTag :: normalApiTags
          val apiVersion = APIV2Version(
            LocalDateTime.ofInstant(version.createdAt.toInstant, ZoneOffset.UTC),
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

          Ok(Json.toJson(apiVersion))
        }
    }

  def showUser(user: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.GlobalScope)(_ => APIV2Queries.userQuery(user).option)
}
object ApiV2Controller {
  implicit val jsonConfig: Aux[Json.MacroOptions] = JsonConfiguration(JsonNaming.SnakeCase)

  sealed trait APIScope
  object APIScope {
    case object GlobalScope                                extends APIScope
    case class ProjectScope(pluginId: String)              extends APIScope
    case class OrganizationScope(organizationName: String) extends APIScope
  }

  case class CreatedKey(name: Option[String], permissions: Seq[String])
  object CreatedKey {
    implicit val format: OFormat[CreatedKey] = Json.configured(jsonConfig).format[CreatedKey]
  }

  case class DeleteKey(key: String)
  object DeleteKey {
    implicit val format: OFormat[DeleteKey] = Json.configured(jsonConfig).format[DeleteKey]
  }

  case class DeployVersionInfo(
      recommended: Option[Boolean],
      createForumPost: Option[Boolean],
      description: Option[String],
      tags: Map[String, String]
  )
  object DeployVersionInfo {
    implicit val format: OFormat[DeployVersionInfo] = Json.configured(jsonConfig).format[DeployVersionInfo]
  }
}
