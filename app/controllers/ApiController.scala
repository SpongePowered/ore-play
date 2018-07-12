package controllers

import java.util.{Base64, UUID}

import akka.http.scaladsl.model.Uri
import javax.inject.Inject
import controllers.sugar.Bakery
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.ProjectApiKeyTable
import form.OreForms
import javax.inject.Inject
import models.api.ProjectApiKey
import models.user.User
import ore.permission.EditApiKeys
import ore.permission.role.RoleTypes
import ore.permission.role.RoleTypes.RoleType
import ore.project.factory.{PendingVersion, ProjectFactory}
import ore.project.io.{InvalidPluginFileException, PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyTypes._
import ore.rest.{OreRestfulApi, OreWrites}
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import util.StatusZ
import util.functional.{EitherT, OptionT, Id}
import util.instances.future._
import util.syntax._
import play.api.libs.json._
import play.api.mvc._
import security.CryptoUtils
import security.spauth.SingleSignOnConsumer
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

/**
  * Ore API (v1)
  */
final class ApiController @Inject()(api: OreRestfulApi,
                                    status: StatusZ,
                                    forms: OreForms,
                                    writes: OreWrites,
                                    factory: ProjectFactory,
                                    implicit override val config: OreConfig,
                                    implicit override val env: OreEnv,
                                    implicit override val service: ModelService,
                                    implicit override val bakery: Bakery,
                                    implicit override val cache: AsyncCacheApi,
                                    implicit override val sso: SingleSignOnConsumer,
                                    implicit override val messagesApi: MessagesApi)(implicit val ec: ExecutionContext)
                                    extends OreBaseController {

  import writes._

  val files = new ProjectFiles(this.env)
  val projectApiKeys = this.service.access[ProjectApiKey](classOf[ProjectApiKey])

  private def ApiResult(json: Option[JsValue]): Result = json.map(Ok(_)).getOrElse(NotFound)

  /**
    * Returns a JSON view of all projects.
    *
    * @param version    API version
    * @return           JSON view of projects
    */
  def listProjects(version: String, categories: Option[String], sort: Option[Int], q: Option[String],
                   limit: Option[Int], offset: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getProjectList(categories, sort, q, limit, offset).map(Ok(_))
      case _ => Future.successful(NotFound)
    }
  }

  /**
    * Returns a JSON view of a Project.
    *
    * @param version    API version
    * @param pluginId   Plugin ID of project
    * @return           Project with Plugin ID
    */
  def showProject(version: String, pluginId: String) = Action.async {
    version match {
      case "v1" => this.api.getProject(pluginId).map(ApiResult)
      case _ => Future.successful(NotFound)
    }
  }

  def createKey(version: String, pluginId: String) =
    (Action andThen AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) async { implicit request =>
      val projectId = request.data.project.id.get
      val res = for {
        keyType <- bindFormOptionT[Future](this.forms.ProjectApiKeyCreate)
        if keyType == Deployment
        exists <- OptionT.liftF(this.projectApiKeys.exists(k => k.projectId === projectId && k.keyType === keyType))
        if !exists
        pak <- OptionT.liftF(
          this.projectApiKeys.add(ProjectApiKey(
            projectId = projectId,
            keyType = keyType,
            value = UUID.randomUUID().toString.replace("-", "")))
        )
      } yield Created(Json.toJson(pak))

      res.getOrElse(BadRequest)
    }

  def revokeKey(version: String, pluginId: String) =
    (AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) { implicit request =>
      val res = for {
        key <- bindFormOptionT[Id](this.forms.ProjectApiKeyRevoke)
        if key.projectId == request.data.project.id.get
      } yield {
        key.remove()
        Ok
      }

      res.getOrElse(BadRequest)
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
  def listVersions(version: String, pluginId: String, channels: Option[String],
                   limit: Option[Int], offset: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getVersionList(pluginId, channels, limit, offset).map(Some.apply).map(ApiResult)
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
  def showVersion(version: String, pluginId: String, name: String) = Action.async {
    version match {
      case "v1" => this.api.getVersion(pluginId, name).map(ApiResult)
      case _ => Future.successful(NotFound)
    }
  }

  private def error(key: String, error: String) = Json.obj("errors" -> Map(key -> List(this.messagesApi(error))))

  def deployVersion(version: String, pluginId: String, name: String) = ProjectAction(pluginId).async { implicit request =>
    version match {
      case "v1" =>
        val projectData = request.data

        bindFormEitherT[Future](this.forms.VersionDeploy)(hasErrors => BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson))).flatMap { formData =>
          val apiKeyTable = TableQuery[ProjectApiKeyTable]
          def queryApiKey(deployment: Rep[ProjectApiKeyType], key: Rep[String], pId: Rep[Int]) = {
            val query = for {
              k <- apiKeyTable if k.value === key && k.projectId === pId && k.keyType === deployment
            } yield {
              k.id
            }
            query.exists
          }

          val compiled = Compiled(queryApiKey _)

          val apiKeyExists: Future[Boolean] = this.service.DB.db.run(compiled(Deployment, formData.apiKey, projectData.project.id.get).result)

          EitherT.liftF(apiKeyExists)
            .filterOrElse(apiKey => !apiKey, Unauthorized(error("apiKey", "api.deploy.invalidKey")))
            .semiFlatMap(_ => projectData.project.versions.exists(_.versionString === name))
            .filterOrElse(identity, BadRequest(error("versionName", "api.deploy.versionExists")))
            .semiFlatMap(_ => projectData.project.owner.user)
            .semiFlatMap(user => user.toMaybeOrganization.semiFlatMap(_.owner.user).getOrElse(user))
            .flatMap { owner =>

              val pluginUpload = this.factory.getUploadError(owner)
                .map(err => BadRequest(error("user", err)))
                .toLeft(PluginUpload.bindFromRequest())
                .flatMap(_.toRight(BadRequest(error("files", "error.noFile"))))

              EitherT.fromEither[Future](pluginUpload).flatMap { data =>
                //TODO: We should get rid of this try
                try {
                  this.factory
                    .processSubsequentPluginUpload(data, owner, projectData.project)
                    .leftMap(err => BadRequest(error("upload", err)))
                }
                catch {
                  case e: InvalidPluginFileException =>
                    EitherT.leftT[Future, PendingVersion](BadRequest(error("upload", e.getMessage)))
                }
              }
            }
            .semiFlatMap { pendingVersion =>
              pendingVersion.createForumPost = formData.createForumPost
              pendingVersion.channelName = formData.channel.name
              formData.changelog.foreach(pendingVersion.underlying.setDescription)
              pendingVersion.complete()
            }
            .map { case (newVersion, channel, tags) =>
              if (formData.recommended)
                projectData.project.setRecommendedVersion(newVersion)
              Created(api.writeVersion(newVersion, projectData.project, channel, None, tags))
            }
        }.merge
      case _ => Future.successful(NotFound)
    }
  }

  def listPages(version: String, pluginId: String, parentId: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getPages(pluginId, parentId).value.map(ApiResult)
      case _ => Future.successful(NotFound)
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
  def listUsers(version: String, limit: Option[Int], offset: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getUserList(limit, offset).map(Ok(_))
      case _ => Future.successful(NotFound)
    }
  }

  /**
    * Returns a JSON view of the specified User.
    *
    * @param version    API version
    * @param username   Username of user
    * @return           User with username
    */
  def showUser(version: String, username: String) = Action.async {
    version match {
      case "v1" => this.api.getUser(username).map(ApiResult)
      case _ => Future.successful(NotFound)
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
  def listTags(version: String, plugin: String, versionName: String) = Action.async {
    version match {
      case "v1" => this.api.getTags(plugin, versionName).value.map(ApiResult)
      case _ => Future.successful(NotFound)
    }
  }

  def tagColor(version: String, id: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getTagColor(id.toInt))
      case _ => NotFound
    }
  }

  /**
    * Returns a JSON statusz endpoint for Ore.
    *
    * @return statusz json
    */
  def showStatusZ = Action(Ok(this.status.json))

  def syncSso() = Action.async { implicit request =>
    val confApiKey = this.config.security.get[String]("sso.apikey")
    val confSecret = this.config.security.get[String]("sso.secret")

    bindFormEitherT[Future](this.forms.SyncSso)(hasErrors => BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson)))
      .filterOrElse(_._3 == confApiKey, BadRequest("API Key not valid")) //_3 is apiKey
      .filterOrElse(
        { case (sso, sig, _) => CryptoUtils.hmac_sha256(confSecret, sso.getBytes("UTF-8")) == sig},
        BadRequest("Signature not matched")
      )
      .map(t => Uri.Query(Base64.getMimeDecoder.decode(t._1))) //_1 is sso
      .semiFlatMap(q => this.users.get(q.get("external_id").get.toInt).value.tupleLeft(q))
      .map { case (query, optUser) =>
        optUser.foreach { user =>
          val email = query.get("email")
          val username = query.get("username")
          val name = query.get("name")
          val avatar_url = query.get("avatar_url")
          val add_groups = query.get("add_groups")

          email.foreach(user.setEmail)
          username.foreach(user.setUsername)
          name.foreach(user.setFullName)
          avatar_url.foreach(user.setAvatarUrl)
          add_groups.foreach { groups =>
            user.setGlobalRoles(
              if (groups.trim == "")
                Set.empty
              else
                groups.split(",").map(group => RoleTypes.withInternalName(group)).toSet[RoleType]
            )
          }

        }

        Ok(Json.obj("status" -> "success"))
      }.merge
  }
}
