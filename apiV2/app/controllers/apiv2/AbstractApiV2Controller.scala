package controllers.apiv2

import java.time.OffsetDateTime

import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.http.Writeable
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{ActionBuilder, ActionFilter, ActionFunction, ActionRefiner, AnyContent, Request, Result}

import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors}
import controllers.{OreBaseController, OreControllerComponents}
import controllers.sugar.CircePlayController
import controllers.sugar.Requests.ApiRequest
import db.impl.query.APIV2Queries
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{OrganizationTable, ProjectTable, UserTable}
import ore.models.api.ApiSession
import ore.permission.Permission
import ore.permission.scope.{GlobalScope, OrganizationScope, ProjectScope, Scope}

import akka.http.scaladsl.model.headers.{Authorization, HttpCredentials}
import cats.data.NonEmptyList
import cats.syntax.all._
import io.circe._
import zio.interop.catz._
import zio.{IO, Task, UIO, ZIO}

abstract class AbstractApiV2Controller(lifecycle: ApplicationLifecycle)(
    implicit oreComponents: OreControllerComponents
) extends OreBaseController
    with CircePlayController {

  implicit def zioMode[R]: scalacache.Mode[ZIO[R, Throwable, *]] =
    scalacache.CatsEffect.modes.async[ZIO[R, Throwable, *]]

  private val resultCache       = scalacache.caffeine.CaffeineCache[IO[Result, Result]]
  private val actionResultCache = scalacache.caffeine.CaffeineCache[Future[Result]]

  lifecycle.addStopHook(() => zioRuntime.unsafeRunToFuture(resultCache.close[Task]()))

  protected def cachingF[R, A, B](
      cacheKey: String
  )(parts: Any*)(fa: ZIO[R, Result, Result])(implicit request: ApiRequest[B]): ZIO[R, Result, Result] =
    resultCache
      .cachingF[ZIO[R, Throwable, *]](
        cacheKey +: parts :+
          request.apiInfo.key.map(_.tokenIdentifier) :+
          //We do both the user and the token for authentication methods that don't use a token
          request.apiInfo.user.map(_.id) :+
          request.body
      )(
        Some(1.minute)
      )(fa.memoize)
      .asError(InternalServerError)
      .flatten

  protected def limitOrDefault(limit: Option[Long], default: Long): Long = math.min(limit.getOrElse(default), default)
  protected def offsetOrZero(offset: Long): Long                         = math.max(offset, 0)

  protected def parseAuthHeader(request: Request[_]): IO[Either[Unit, Result], HttpCredentials] = {
    def unAuth[A: Writeable](msg: A) = Unauthorized(msg).withHeaders(WWW_AUTHENTICATE -> "OreApi")

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
      def unAuth(msg: String) = Unauthorized(ApiError(msg)).withHeaders(WWW_AUTHENTICATE -> "OreApi")

      val authRequest = for {
        creds <- parseAuthHeader(request)
          .mapError(_.leftMap(_ => unAuth("No authorization specified")).merge)
        token <- ZIO
          .fromOption(creds.params.get("session"))
          .asError(unAuth("No session specified"))
        info <- service
          .runDbCon(APIV2Queries.getApiAuthInfo(token).option)
          .get
          .asError(unAuth("Invalid session"))
        res <- {
          if (info.expires.isBefore(OffsetDateTime.now())) {
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
      scope    <- apiScopeToRealScope(apiScope).asError(NotFound)
      perms    <- request.permissionIn(scope)
    } yield (apiScope, perms)

  def permApiAction(perms: Permission, scope: APIScope): ActionFilter[ApiRequest] = new ActionFilter[ApiRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApiRequest[A]): Future[Option[Result]] = {
      //Techically we could make this faster by first checking if the global perms have the needed perms,
      //but then we wouldn't get the 404 on a non existent scope.
      val scopePerms: IO[Unit, Permission] =
        apiScopeToRealScope(scope).flatMap(request.permissionIn[Scope, IO[Unit, *]](_))
      val res = scopePerms.asError(NotFound).ensure(Forbidden)(_.has(perms))

      zioToFuture(res.either.map(_.swap.toOption))
    }
  }

  def cachingAction: ActionFunction[ApiRequest, ApiRequest] =
    new ActionFunction[ApiRequest, ApiRequest] {
      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](request: ApiRequest[A], block: ApiRequest[A] => Future[Result]): Future[Result] = {
        import scalacache.modes.scalaFuture._
        require(request.method == "GET")

        request.request.target

        actionResultCache
          .caching[Future](
            request.path,
            request.apiInfo.key.map(_.tokenIdentifier),
            request.apiInfo.user.map(_.id),
            request.queryString.toSeq.sortBy(_._1)
          )(Some(1.minute))(block(request))
          .flatten
      }
    }

  def ApiAction(perms: Permission, scope: APIScope): ActionBuilder[ApiRequest, AnyContent] =
    Action.andThen(apiAction).andThen(permApiAction(perms, scope))

  def CachingApiAction(perms: Permission, scope: APIScope): ActionBuilder[ApiRequest, AnyContent] =
    ApiAction(perms, scope).andThen(cachingAction)

  def withUndefined[A: Decoder](cursor: ACursor): Decoder.AccumulatingResult[Option[A]] = {
    import cats.instances.either._
    import cats.instances.option._
    val res = if (cursor.succeeded) Some(cursor.as[A]) else None

    res.sequence.toValidatedNel
  }
}
