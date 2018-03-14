package controllers

import java.util.UUID

import javax.inject.Inject
import controllers.sugar.Bakery
import db.ModelService
import db.impl.OrePostgresDriver.api._
import form.OreForms
import models.api.ProjectApiKey
import models.user.User
import ore.permission.EditApiKeys
import ore.project.factory.ProjectFactory
import ore.project.io.{InvalidPluginFileException, PluginUpload, ProjectFiles}
import ore.rest.ProjectApiKeyTypes._
import ore.rest.{OreRestfulApi, OreWrites}
import ore.{OreConfig, OreEnv}
import play.api.i18n.{Lang, MessagesApi}
import util.StatusZ
import play.api.libs.json._
import play.api.mvc._
import security.spauth.SingleSignOnConsumer

import scala.concurrent.ExecutionContext.Implicits.global

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
                   limit: Option[Int], offset: Option[Int]) = Action {
    version match {
      case "v1" => Ok(this.api.getProjectList(categories, sort, q, limit, offset))
      case _ => NotFound
    }
  }

  /**
    * Returns a JSON view of a Project.
    *
    * @param version    API version
    * @param pluginId   Plugin ID of project
    * @return           Project with Plugin ID
    */
  def showProject(version: String, pluginId: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getProject(pluginId))
      case _ => NotFound
    }
  }

  def createKey(version: String, pluginId: String) = {
    (Action andThen AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) { implicit request =>
      val project = request.project
      this.forms.ProjectApiKeyCreate.bindFromRequest().fold(
        _ => BadRequest,
        {
          case keyType@Deployment =>
            if (this.projectApiKeys.exists(k => k.projectId === project.id.get && k.keyType === keyType))
              BadRequest
            else {
              Created(Json.toJson(this.projectApiKeys.add(ProjectApiKey(
                projectId = project.id.get,
                keyType = keyType,
                value = UUID.randomUUID().toString.replace("-", "")))))
            }
          case _ =>
            BadRequest
        }
      )
    }
  }

  def revokeKey(version: String, pluginId: String) = {
    (AuthedProjectActionById(pluginId) andThen ProjectPermissionAction(EditApiKeys)) { implicit request =>
      this.forms.ProjectApiKeyRevoke.bindFromRequest().fold(
        _ => BadRequest,
        key => {
          if (key.projectId != request.project.id.get)
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
                   limit: Option[Int], offset: Option[Int]) = Action {
    version match {
      case "v1" => ApiResult(this.api.getVersionList(pluginId, channels, limit, offset))
      case _ => NotFound
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
  def showVersion(version: String, pluginId: String, name: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getVersion(pluginId, name))
      case _ => NotFound
    }
  }

  private def error(key: String, error: String) = Json.obj("errors" -> Map(key -> List(this.messagesApi(error))))

  def deployVersion(version: String, pluginId: String, name: String) = ProjectAction(pluginId) { implicit request =>
    version match {
      case "v1" =>
        val project = request.project
        this.forms.VersionDeploy.bindFromRequest().fold(
          hasErrors => BadRequest(Json.obj("errors" -> hasErrors.errorsAsJson)),
          formData => {
            if (!this.projectApiKeys.exists(k => k.keyType === Deployment && k.value === formData.apiKey && project.id.isDefined && k.projectId === project.id.get))
              Unauthorized(error("apiKey", "api.deploy.invalidKey"))
            else if (project.versions.exists(_.versionString === name))
              BadRequest(error("versionName", "api.deploy.versionExists"))
            else {
              var user: User = project.owner
              if (user.isOrganization)
                user = user.toOrganization.owner
              this.factory.getUploadError(user) match {
                case Some(err) => BadRequest(error("user", err))
                case None => PluginUpload.bindFromRequest() match {
                  case None => BadRequest(error("files", "error.noFile"))
                  case Some(uploadData) =>
                    try {
                      this.factory.processSubsequentPluginUpload(uploadData, user, project).fold(
                        err => BadRequest(error("upload", err)),
                        version => {
                          version.createForumPost = formData.createForumPost
                          version.channelName = formData.channel.name
                          val newVersion = version.complete().get
                          if (formData.recommended)
                            project.recommendedVersion = newVersion
                          Created(Json.toJson(newVersion))
                        }
                      )
                    } catch {
                      case e: InvalidPluginFileException =>
                        BadRequest(error("upload", e.getMessage))
                    }
                }
              }
            }
          }
        )
      case _ =>
        NotFound
    }
  }

  def listPages(version: String, pluginId: String, parentId: Option[Int]) = Action {
    version match {
      case "v1" => ApiResult(this.api.getPages(pluginId, parentId))
      case _ => NotFound
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
  def listUsers(version: String, limit: Option[Int], offset: Option[Int]) = Action {
    version match {
      case "v1" => Ok(this.api.getUserList(limit, offset))
      case _ => NotFound
    }
  }

  /**
    * Returns a JSON view of the specified User.
    *
    * @param version    API version
    * @param username   Username of user
    * @return           User with username
    */
  def showUser(version: String, username: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getUser(username))
      case _ => NotFound
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
  def listTags(version: String, plugin: String, versionName: String) = Action {
    version match {
      case "v1" => ApiResult(this.api.getTags(plugin, versionName))
      case _ => NotFound
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

}
