package controllers

import java.util.{Base64, UUID}
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.AsyncCacheApi
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._

import controllers.sugar.Bakery
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectApiKeyTable
import db.{ModelService, ObjectReference}
import form.OreForms
import models.api.ProjectApiKey
import models.user.{LoggedAction, UserActionLogger}
import ore.permission.role.RoleType
import ore.permission.{EditApiKeys, ReviewProjects}
import ore.project.factory.{PendingVersion, ProjectFactory}
import ore.project.io.{InvalidPluginFileException, PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyType._
import ore.rest.{OreRestfulApi, OreWrites, ProjectApiKeyType}
import ore.{OreConfig, OreEnv}
import security.CryptoUtils
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import _root_.util.StatusZ

import akka.http.scaladsl.model.Uri
import cats.data.{EitherT, OptionT}
import cats.instances.future._
import cats.syntax.all._
import slick.lifted.Compiled

/**
  * Ore API (v1)
  */
final class ApiController @Inject()(
    api: OreRestfulApi,
    status: StatusZ,
    forms: OreForms,
    factory: ProjectFactory
)(
    implicit val ec: ExecutionContext,
    config: OreConfig,
    env: OreEnv,
    service: ModelService,
    bakery: Bakery,
    cache: AsyncCacheApi,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
) extends OreBaseController
    with OreWrites {

  val files                                      = new ProjectFiles(this.env)
  val projectApiKeys: ModelAccess[ProjectApiKey] = this.service.access[ProjectApiKey]()
  val Logger                                     = play.api.Logger("SSO")

  private def ApiResult(json: Option[JsValue]): Result = json.map(Ok(_)).getOrElse(NotFound)

  /**
    * Returns a JSON view of all projects.
    *
    * @param version    API version
    * @return           JSON view of projects
    */
  def listProjects(
      version: String,
      categories: Option[String],
      sort: Option[Int],
      q: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getProjectList(categories, sort, q, limit, offset).map(Ok(_))
      case _    => Future.successful(NotFound)
    }
  }

  /**
    * Returns a JSON view of a Project.
    *
    * @param version    API version
    * @param pluginId   Plugin ID of project
    * @return           Project with Plugin ID
    */
  def showProject(version: String, pluginId: String): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getProject(pluginId).map(ApiResult)
      case _    => Future.successful(NotFound)
    }
  }

  def createKey(version: String, pluginId: String): Action[AnyContent] =
    Action.andThen(AuthedProjectActionById(pluginId)).andThen(ProjectPermissionAction(EditApiKeys)).async {
      implicit request =>
        version match {
          case "v1" =>
            val projectId = request.data.project.id.value
            val res = for {
              keyType <- forms.ProjectApiKeyCreate.bindOptionT[Future]
              if keyType == Deployment
              exists <- OptionT
                .liftF(this.projectApiKeys.exists(k => k.projectId === projectId && k.keyType === keyType))
              if !exists
              pak <- OptionT.liftF(
                this.projectApiKeys.add(
                  ProjectApiKey(
                    projectId = projectId,
                    keyType = keyType,
                    value = UUID.randomUUID().toString.replace("-", "")
                  )
                )
              )
              _ <- OptionT.liftF(
                UserActionLogger.log(
                  request.request,
                  LoggedAction.ProjectSettingsChanged,
                  projectId,
                  s"${request.user.name} created a new ApiKey",
                  ""
                )
              )
            } yield Created(Json.toJson(pak))
            res.getOrElse(BadRequest)
          case _ => Future.successful(NotFound)
        }
    }

  def revokeKey(version: String, pluginId: String): Action[AnyContent] =
    AuthedProjectActionById(pluginId).andThen(ProjectPermissionAction(EditApiKeys)).async { implicit request =>
      version match {
        case "v1" =>
          val res = for {
            key <- forms.ProjectApiKeyRevoke.bindOptionT[Future]
            if key.projectId == request.data.project.id.value
            _ <- OptionT.liftF(service.delete(key))
            _ <- OptionT.liftF(
              UserActionLogger.log(
                request.request,
                LoggedAction.ProjectSettingsChanged,
                request.data.project.id.value,
                s"${request.user.name} removed an ApiKey",
                ""
              )
            )
          } yield Ok
          res.getOrElse(BadRequest)
        case _ => Future.successful(NotFound)
      }
    }

  /**
    * Returns a JSON view of Versions meeting the specified criteria.
    *
    * @param version  API version string
    * @param pluginId Project plugin ID
    * @param channels Channels to get versions from
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         List of versions
    */
  def listVersions(
      version: String,
      pluginId: String,
      channels: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ): Action[AnyContent] = Action.async {
    version match {
      case "v1" =>
        this.api.getVersionList(pluginId, channels, limit, offset, onlyPublic = true).map(Some.apply).map(ApiResult)
      case _ => Future.successful(NotFound)
    }
  }

  /**
    * Almost like [[listVersions()]] but more intended for internal use. Shows all versions, but need authentification.
    *
    * @param version  API version string
    * @param pluginId Project plugin ID
    * @param channels Channels to get versions from
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         List of versions
    */
  def listAllVersions(
      version: String,
      pluginId: String,
      channels: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ): Action[AnyContent] =
    AuthedProjectActionById(pluginId).andThen(PermissionAction(ReviewProjects)).async {
      version match {
        case "v1" =>
          this.api.getVersionList(pluginId, channels, limit, offset, onlyPublic = false).map(Some.apply).map(ApiResult)
        case _ => Future.successful(NotFound)
      }
    }

  /**
    * Shows the specified Project Version.
    *
    * @param version  API version
    * @param pluginId Project plugin ID
    * @param name     Version name
    * @return         JSON view of Version
    */
  def showVersion(version: String, pluginId: String, name: String): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getVersion(pluginId, name).map(ApiResult)
      case _    => Future.successful(NotFound)
    }
  }

  private def error(key: String, error: String)(implicit messages: Messages) =
    Json.obj("errors" -> Map(key -> List(messages(error))))

  def deployVersion(version: String, pluginId: String, name: String): Action[AnyContent] =
    ProjectAction(pluginId).async { implicit request =>
      version match {
        case "v1" =>
          val projectData = request.data
          val project     = projectData.project

          forms.VersionDeploy
            .bindEitherT[Future](
              hasErrors => BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson))
            )
            .flatMap { formData =>
              val apiKeyTable = TableQuery[ProjectApiKeyTable]
              def queryApiKey(deployment: Rep[ProjectApiKeyType], key: Rep[String], pId: Rep[ObjectReference]) = {
                val query = for {
                  k <- apiKeyTable if k.value === key && k.projectId === pId && k.keyType === deployment
                } yield {
                  k.id
                }
                query.exists
              }

              val compiled = Compiled(queryApiKey _)

              val apiKeyExists: Future[Boolean] =
                this.service.runDBIO(compiled((Deployment, formData.apiKey, project.id.value)).result)

              EitherT
                .liftF(apiKeyExists)
                .ensure(Unauthorized(error("apiKey", "api.deploy.invalidKey")))(identity)
                .semiflatMap(_ => project.versions.exists(_.versionString === name))
                .ensure(BadRequest(error("versionName", "api.deploy.versionExists")))(nameExists => !nameExists)
                .semiflatMap(_ => project.owner.user)
                .semiflatMap(user => user.toMaybeOrganization.semiflatMap(_.owner.user).getOrElse(user))
                .flatMap { owner =>
                  val pluginUpload = this.factory
                    .getUploadError(owner)
                    .map(err => BadRequest(error("user", err)))
                    .toLeft(PluginUpload.bindFromRequest())
                    .flatMap(_.toRight(BadRequest(error("files", "error.noFile"))))

                  EitherT.fromEither[Future](pluginUpload).flatMap { data =>
                    //TODO: We should get rid of this try
                    try {
                      this.factory
                        .processSubsequentPluginUpload(data, owner, project)
                        .leftMap(err => BadRequest(error("upload", err)))
                    } catch {
                      case e: InvalidPluginFileException =>
                        EitherT.leftT[Future, PendingVersion](BadRequest(error("upload", e.getMessage)))
                    }
                  }
                }
                .semiflatMap { pendingVersion =>
                  pendingVersion.createForumPost = formData.createForumPost
                  pendingVersion.channelName = formData.channel.name
                  formData.changelog.fold(Future.successful(pendingVersion)) { changelog =>
                    service
                      .updateIfDefined(pendingVersion.underlying.copy(description = Some(changelog)))
                      .map(newVersion => pendingVersion.copy(underlying = newVersion))
                  }
                }
                .semiflatMap(_.complete())
                .semiflatMap {
                  case (newVersion, channel, tags) =>
                    val update =
                      if (formData.recommended)
                        service.update(project.copy(recommendedVersionId = Some(newVersion.id.value)))
                      else Future.unit

                    update.as(Created(api.writeVersion(newVersion, project, channel, None, tags)))
                }
            }
            .merge
        case _ => Future.successful(NotFound)
      }
    }

  def listPages(version: String, pluginId: String, parentId: Option[Int]): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getPages(pluginId, parentId).value.map(ApiResult)
      case _    => Future.successful(NotFound)
    }
  }

  /**
    * Returns a JSON view of Ore Users.
    *
    * @param version  API version
    * @param limit    Amount of users to get
    * @param offset   Offset to drop
    * @return         List of users
    */
  def listUsers(version: String, limit: Option[Int], offset: Option[Int]): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getUserList(limit, offset).map(Ok(_))
      case _    => Future.successful(NotFound)
    }
  }

  /**
    * Returns a JSON view of the specified User.
    *
    * @param version    API version
    * @param username   Username of user
    * @return           User with username
    */
  def showUser(version: String, username: String): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getUser(username).map(ApiResult)
      case _    => Future.successful(NotFound)
    }
  }

  /**
    * Get the tags for a single version
    *
    * @param version     API Version
    * @param plugin      Plugin Id
    * @param versionName Version of the plugin
    * @return Tags for the version of the plugin
    */
  def listTags(version: String, plugin: String, versionName: String): Action[AnyContent] = Action.async {
    version match {
      case "v1" => this.api.getTags(plugin, versionName).value.map(ApiResult)
      case _    => Future.successful(NotFound)
    }
  }

  def tagColor(version: String, id: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getTagColor(id.toInt))
      case _    => NotFound
    }
  }

  /**
    * Returns a JSON statusz endpoint for Ore.
    *
    * @return statusz json
    */
  def showStatusZ = Action(Ok(this.status.json))

  def syncSso(): Action[AnyContent] = Action.async { implicit request =>
    val confApiKey = this.config.security.get[String]("sso.apikey")
    val confSecret = this.config.security.get[String]("sso.secret")

    Logger.debug("Sync Request received")

    forms.SyncSso
      .bindEitherT[Future](hasErrors => BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson)))
      .ensure(BadRequest("API Key not valid"))(_._3 == confApiKey) //_3 is apiKey
      .ensure(BadRequest("Signature not matched"))(
        { case (ssoStr, sig, _) => CryptoUtils.hmac_sha256(confSecret, ssoStr.getBytes("UTF-8")) == sig }
      )
      .map(t => Uri.Query(Base64.getMimeDecoder.decode(t._1))) //_1 is sso
      .semiflatMap { q =>
        Logger.debug("Sync Payload: " + q)
        users.get(q.get("external_id").get.toLong).value.tupleLeft(q)
      }
      .semiflatMap {
        case (query, optUser) =>
          Logger.debug("Sync user found: " + optUser.isDefined)
          optUser
            .map { user =>
              val email      = query.get("email")
              val username   = query.get("username")
              val fullName   = query.get("name")
              val add_groups = query.get("add_groups")

              service.update(
                user.copy(
                  email = email.orElse(user.email),
                  name = username.getOrElse(user.name),
                  fullName = fullName.orElse(user.fullName),
                  globalRoles = add_groups.fold(user.globalRoles) { groups =>
                    if (groups.trim.isEmpty) Nil
                    else groups.split(",").flatMap(RoleType.withValueOpt).toList
                  }
                )
              )
            }
            .getOrElse(Future.unit)
            .as(Ok(Json.obj("status" -> "success")))
      }
      .merge
  }
}
