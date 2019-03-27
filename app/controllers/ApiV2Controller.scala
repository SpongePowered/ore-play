package controllers

import scala.language.higherKinds

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.AsyncCacheApi
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import controllers.sugar.Bakery
import controllers.sugar.Requests.{ApiAuthInfo, ApiRequest, AuthApiRequest}
import db.ModelService
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.UserTable
import db.query.APIV2Queries
import models.api.ApiKey
import models.querymodels.APIV2Project
import ore.permission.Permission
import ore.project.{Category, ProjectSortingStrategy}
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}

import cats.Traverse
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.instances.list._
import cats.instances.option._
import cats.syntax.all._
import slick.lifted.TableQuery

class ApiV2Controller(
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
      val token = request.headers
        .get(AUTHORIZATION)
        .map(_.split(" ", 2))
        .filter(_.length == 2)
        .map(arr => arr.head -> arr(1))
        .collect { case ("ApiKey", key) => key }

      token
        .fold(EitherT.rightT[IO, Result](ApiRequest(None, request))) { token =>
          OptionT(
            service
              .runDBIO(
                ModelView
                  .later(ApiKey)
                  .find(_.token === token)
                  .join(TableQuery[UserTable])
                  .on(_.ownerId === _.id)
                  .result
                  .headOption
              )
          ).map(t => ApiRequest(Some(ApiAuthInfo(t._2, t._1.permissions)), request)).toRight(Unauthorized: Result)
        }
        .value
        .unsafeToFuture()
    }
  }

  def authApiAction: ActionRefiner[ApiRequest, AuthApiRequest] = new ActionRefiner[ApiRequest, AuthApiRequest] {
    def executionContext: ExecutionContext = ec
    override protected def refine[A](request: ApiRequest[A]): Future[Either[Result, AuthApiRequest[A]]] = {
      Future.successful(
        request.apiInfo.fold[Either[Result, AuthApiRequest[A]]](Left(Unauthorized)) { info =>
          Right(AuthApiRequest(info, request))
        }
      )
    }
  }

  def apiOptDbAction[A: Writes](
      action: ApiRequest[AnyContent] => doobie.ConnectionIO[Option[A]]
  ): Action[AnyContent] =
    Action.andThen(apiAction).asyncF { request =>
      service.runDbCon(action(request)).map(_.fold(NotFound: Result)(a => Ok(Json.toJson(a))))
    }

  def apiEitherDbAction[A: Writes](
      action: ApiRequest[AnyContent] => Either[Result, doobie.ConnectionIO[Vector[A]]]
  ): Action[AnyContent] =
    Action.andThen(apiAction).asyncF { request =>
      action(request).bimap(IO.pure, service.runDbCon).map(_.map(a => Ok(Json.toJson(a)))).merge
    }

  def apiDbAction[A: Writes](action: ApiRequest[AnyContent] => doobie.ConnectionIO[Vector[A]]): Action[AnyContent] =
    Action.andThen(apiAction).asyncF { request =>
      service.runDbCon(action(request)).map(xs => Ok(Json.toJson(xs)))
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
    apiEitherDbAction[APIV2Project] { request =>
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
            request.permission.has(Permission.SeeHidden),
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
    apiOptDbAction { request =>
      APIV2Queries
        .projectQuery(
          Some(pluginId),
          Nil,
          Nil,
          None,
          None,
          request.permission.has(Permission.SeeHidden),
          request.user.map(_.id),
          ProjectSortingStrategy.Default,
          orderWithRelevance = false,
          1,
          0
        )
        .option
    }

  def showMembers(pluginId: String, limit: Option[Long], offset: Long): Action[AnyContent] =
    apiDbAction(
      _ =>
        APIV2Queries
          .projectMembers(pluginId, limitOrDefault(limit, 25), offset)
          .map(mem => mem.copy(roles = mem.roles.sortBy(_.permissions: Long)))
          .to[Vector]
    )

  def listVersions(
      pluginId: String,
      tags: Seq[String],
      limit: Option[Long],
      offset: Long
  ): Action[AnyContent] =
    apiDbAction(
      _ =>
        APIV2Queries
          .versionQuery(
            pluginId,
            None,
            tags.toList,
            limitOrDefault(limit, config.ore.projects.initVersionLoad.toLong),
            offset
          )
          .to[Vector]
    )

  def showVersion(pluginId: String, name: String): Action[AnyContent] =
    apiOptDbAction(_ => APIV2Queries.versionQuery(pluginId, Some(name), Nil, 1, 0).option)

  def deployVersion(pluginId: String, name: String): Action[AnyContent] = TODO

  def showUser(user: String): Action[AnyContent] = apiOptDbAction(_ => APIV2Queries.userQuery(user).option)
}
