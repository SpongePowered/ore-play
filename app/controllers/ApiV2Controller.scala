package controllers

import scala.language.higherKinds

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.Inject

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.AsyncCacheApi
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import controllers.ApiV2Controller.APIScope
import controllers.sugar.Bakery
import controllers.sugar.Requests.{ApiAuthInfo, ApiRequest}
import db.ModelService
import db.access.ModelView
import db.query.{APIV2Queries, UserQueries}
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationTable, ProjectTableMain}
import models.api.{ApiKey, ApiSession}
import models.querymodels.APIV2Project
import ore.permission.{NamedPermission, Permission}
import ore.permission.scope.{GlobalScope, OrganizationScope, ProjectScope}
import ore.project.{Category, ProjectSortingStrategy}
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}

import cats.Traverse
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.instances.list._
import cats.instances.option._
import cats.syntax.all._

class ApiV2Controller @Inject()(
    implicit val ec: ExecutionContext,
    env: OreEnv,
    config: OreConfig,
    service: ModelService,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    cache: AsyncCacheApi
) extends OreBaseController {

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

      lazy val authUrl = routes.ApiV2Controller.authenticate().absoluteURL()(request)
      lazy val unAuth =
        Unauthorized.withHeaders(WWW_AUTHENTICATE -> authUrl)

      optToken
        .fold(EitherT.leftT[IO, ApiRequest[A]](unAuth)) { token =>
          OptionT(service.runDbCon(UserQueries.getApiAuthInfo(token).option))
            .toRight(unAuth)
            .flatMap { info =>
              if (info.expires.isBefore(Instant.now())) {
                EitherT
                  .left[ApiAuthInfo](service.deleteWhere(ApiSession)(_.token === token))
                  .leftMap { _ =>
                    Unauthorized(Json.obj("message" -> "Api session expired")).withHeaders(WWW_AUTHENTICATE -> authUrl)
                  }
              } else EitherT.rightT[IO, Result](info)
            }
            .map(info => ApiRequest(info, request))
        }
        .value
        .unsafeToFuture()
    }
  }

  def permApiAction(perms: Permission, scope: APIScope): ActionFilter[ApiRequest] = new ActionFilter[ApiRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApiRequest[A]): Future[Option[Result]] = {
      //Techically we could make this faster by first checking if the global perms have the needed perms,
      //but then we wouldn't get the 404 on a non existent scope.
      val scopePerms = scope match {
        case APIScope.GlobalScope => OptionT.liftF(request.permissionIn(GlobalScope))
        case APIScope.ProjectScope(pluginId) =>
          OptionT(
            service.runDBIO(TableQuery[ProjectTableMain].filter(_.pluginId === pluginId).map(_.id).result.headOption)
          ).semiflatMap(id => request.permissionIn(ProjectScope(id)))
        case APIScope.OrganizationScope(organizationName) =>
          OptionT(
            service.runDBIO(
              TableQuery[OrganizationTable].filter(_.name === organizationName).map(_.id).result.headOption
            )
          ).semiflatMap(id => request.permissionIn(OrganizationScope(id)))
      }

      scopePerms.toRight(NotFound).ensure(Forbidden)(_.has(perms)).swap.toOption.value.unsafeToFuture()
    }
  }

  def ApiAction(perms: Permission, scope: APIScope): ActionBuilder[ApiRequest, AnyContent] =
    Action.andThen(apiAction).andThen(permApiAction(perms, scope))

  def apiOptDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[Option[A]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(_.fold(NotFound: Result)(a => Ok(Json.toJson(a))))
    }

  def apiEitherDbAction[A: Writes](perms: Permission, scope: APIScope)(
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[Vector[A]]]
  ): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon).map(_.map(a => Ok(Json.toJson(a)))).merge
    }

  def apiDbAction[A: Writes](
      perms: Permission,
      scope: APIScope
  )(action: ApiRequest[AnyContent] => doobie.ConnectionIO[Vector[A]]): Action[AnyContent] =
    ApiAction(perms, scope).asyncF { request =>
      service.runDbCon(action(request)).map(xs => Ok(Json.toJson(xs)))
    }

  def authenticate(): Action[AnyContent] = OreAction.asyncEitherT { implicit request =>
    def expiration(duration: FiniteDuration) = service.theTime.toInstant.plusSeconds(duration.toSeconds)

    lazy val sessionExpiration       = expiration(config.ore.api.sessionExpiration)
    lazy val publicSessionExpiration = expiration(config.ore.api.publicSessionExpiration)

    val optApiKey = request.headers
      .get(AUTHORIZATION)
      .map(_.split(" ", 2))
      .filter(_.length == 2)
      .map(arr => arr.head -> arr(1))
      .collect { case ("ApiKey", key) => key }

    val uuidToken = UUID.randomUUID().toString

    val sessionToInsert = (request.currentUser, optApiKey) match {
      case (_, Some(key)) =>
        ModelView.now(ApiKey).find(_.token === key).map { key =>
          "key" -> ApiSession(uuidToken, Some(key.id), Some(key.ownerId), sessionExpiration)
        }
      case (Some(user), None) =>
        OptionT.pure[IO]("user" -> ApiSession(uuidToken, None, Some(user.id), sessionExpiration))
      case (None, None) =>
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
      .toRight(Unauthorized.withHeaders(WWW_AUTHENTICATE -> routes.ApiV2Controller.authenticate().absoluteURL()))
  }

  def createKey(permissions: Seq[String]): Action[AnyContent] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope).asyncF { implicit request =>
      import cats.instances.option._
      import cats.instances.vector._

      permissions.toVector.traverse(NamedPermission.withNameOption).fold(IO.pure(BadRequest: Result)) { perms =>
        val perm = Permission(perms.map(_.permission): _*)
        service
          .insert(ApiKey(request.user.get.id, UUID.randomUUID().toString, perm))
          .map(key => Ok(Json.obj("key" -> key.token)))
      }
    }

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
    apiEitherDbAction[APIV2Project](Permission.ViewPublicInfo, APIScope.GlobalScope) { request =>
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
            0
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

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    apiDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
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
    apiDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
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

  def showVersion(pluginId: String, name: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.ProjectScope(pluginId)) { _ =>
      APIV2Queries.versionQuery(pluginId, Some(name), Nil, 1, 0).option
    }

  def deployVersion(pluginId: String, name: String): Action[AnyContent] = TODO

  def showUser(user: String): Action[AnyContent] =
    apiOptDbAction(Permission.ViewPublicInfo, APIScope.GlobalScope)(_ => APIV2Queries.userQuery(user).option)
}
object ApiV2Controller {
  sealed trait APIScope
  object APIScope {
    case object GlobalScope                                extends APIScope
    case class ProjectScope(pluginId: String)              extends APIScope
    case class OrganizationScope(organizationName: String) extends APIScope
  }
}
