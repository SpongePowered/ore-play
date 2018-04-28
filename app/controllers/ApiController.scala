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
import ore.project.factory.ProjectFactory
import ore.project.io.{InvalidPluginFileException, PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyTypes._
import ore.rest.{OreRestfulApi, OreWrites}
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import util.StatusZ
import play.api.libs.json._
import play.api.mvc._
import security.CryptoUtils
import security.spauth.SingleSignOnConsumer
import slick.lifted.Compiled

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
                                    implicit override val messagesApi: MessagesApi)
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

  def createKey(version: String, pluginId: String) = {
    (Action andThen AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) async { implicit request =>
      val data = request.data
      this.forms.ProjectApiKeyCreate.bindFromRequest().fold( _ => Future.successful(BadRequest),
        {
          case keyType@Deployment =>
            this.projectApiKeys.exists(k => k.projectId === data.project.id.get && k.keyType === keyType)
              .flatMap { exists =>
                if (exists) Future.successful(BadRequest)
                else {
                  this.projectApiKeys.add(ProjectApiKey(
                    projectId = data.project.id.get,
                    keyType = keyType,
                    value = UUID.randomUUID().toString.replace("-", ""))).map { pak =>
                    Created(Json.toJson(pak))
                  }
                }
              }

          case _ => Future.successful(BadRequest)
        }
      )
    }
  }

  def revokeKey(version: String, pluginId: String) = {
    (AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) { implicit request =>
      this.forms.ProjectApiKeyRevoke.bindFromRequest().fold(
        _ => BadRequest,
        key => {
          if (key.projectId != request.data.project.id.get)
            BadRequest
          else {
            key.remove()
            Ok
          }
        }
      )
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
  def listVersions(version: String, pluginId: String, channels: Option[String],
                   limit: Option[Int], offset: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getVersionList(pluginId, channels, limit, offset).map(ApiResult)
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
        this.forms.VersionDeploy.bindFromRequest().fold(
          hasErrors => Future.successful(BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson))),
          formData => {

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
            val dep = for {
              (apiKey, versionExists) <- apiKeyExists zip
                                         projectData.project.versions.exists(_.versionString === name)
            } yield {
              if (!apiKey) Future.successful(Unauthorized(error("apiKey", "api.deploy.invalidKey")))
              else if (versionExists) Future.successful(BadRequest(error("versionName", "api.deploy.versionExists")))
              else {

                def upload(user: User): Future[Result] = {

                 val pending = Right(user).flatMap { user =>
                    this.factory.getUploadError(user)
                      .map(err => BadRequest(error("user", err)))
                      .toLeft(PluginUpload.bindFromRequest())
                  } flatMap {
                    case None => Left(BadRequest(error("files", "error.noFile")))
                    case Some(uploadData) => Right(uploadData)
                  } match {
                    case Left(err) => Future.successful(Left(err))
                    case Right(data) =>
                      try {
                        this.factory.processSubsequentPluginUpload(data, user, projectData.project).map {
                          case Left(err) => Left(BadRequest(error("upload", err)))
                          case Right(pv) => Right(pv)
                        }
                      } catch {
                        case e: InvalidPluginFileException =>
                          Future.successful(Left(BadRequest(error("upload", e.getMessage))))
                      }
                  }
                  pending flatMap {
                    case Left(err) => Future.successful(err)
                    case Right(pendingVersion) =>
                      pendingVersion.createForumPost = formData.createForumPost
                      pendingVersion.channelName = formData.channel.name
                      formData.changelog.foreach(pendingVersion.underlying.setDescription)
                      pendingVersion.complete().map { newVersion =>
                        if (formData.recommended)
                          projectData.project.setRecommendedVersion(newVersion._1)
                        Created(api.writeVersion(newVersion._1, projectData.project, newVersion._2, None, newVersion._3))
                      }
                  }
                }

                for {
                  user <- projectData.project.owner.user
                  orga <- user.toMaybeOrganization
                  owner <- orga.map(_.owner.user).getOrElse(Future.successful(user))
                  result <- upload(owner)
                } yield {
                  result
                }
              }
            }
            dep.flatten
          }
        )
      case _ => Future.successful(NotFound)
    }
  }

  def listPages(version: String, pluginId: String, parentId: Option[Int]) = Action.async {
    version match {
      case "v1" => this.api.getPages(pluginId, parentId).map(ApiResult)
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
      case "v1" => this.api.getTags(plugin, versionName).map(ApiResult)
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
    this.forms.SyncSso.bindFromRequest.fold(
      hasErrors => Future.successful(BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson))),
      success = formData => {
        val sso = formData._1
        val sig = formData._2
        val apiKey = formData._3


        val confApiKey = this.config.security.get[String]("sso.apikey")
        val confSecret = this.config.security.get[String]("sso.secret")

        if (apiKey != confApiKey) {
          Future.successful(BadRequest("API Key not valid"))
        } else if (CryptoUtils.hmac_sha256(confSecret, sso.getBytes("UTF-8")) != sig) {
          Future.successful(BadRequest("Signature not matched"))
        } else {
          val query = Uri.Query(Base64.getMimeDecoder.decode(sso))

          val external_id = query.get("external_id")
          val email = query.get("email")
          val username = query.get("username")
          val name = query.get("name")
          val avatar_url = query.get("avatar_url")
          val add_groups = query.get("add_groups")

          this.users.get(external_id.get.toInt).map { optUser =>
            if (optUser.isDefined) {
              val user = optUser.get

              email.foreach(user.setEmail)
              username.foreach(user.setUsername)
              name.foreach(user.setFullName)
              avatar_url.foreach(user.setAvatarUrl)
              add_groups.foreach(groups =>
                user.setGlobalRoles(
                    if(groups.trim == "")
                      Set.empty
                    else
                      groups.split(",").map(group => RoleTypes.withInternalName(group)).toSet[RoleType]
                )
              )
            }

            Ok(Json.obj("status" -> "success"))
          }
        }
      }
    )
  }
}
