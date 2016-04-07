package controllers

import db.Storage
import models.project.{Categories, Project}
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Failure, Success}

class Api extends Controller {

  implicit val projectWrites = new Writes[Project] {
    def writes(project: Project) = {
      var channelInfo: Seq[Map[String, String]] = null
      Storage.now(project.getChannels) match {
        case Failure(thrown) => throw thrown
        case Success(channels) => channelInfo = for (channel <- channels) yield {
          Map("name" -> channel.getName, "color" -> channel.getColor.hex)
        }
      }

      val category = project.getCategory
      Json.obj(
        "pluginId" -> project.pluginId,
        "createdAt" -> project.prettyDate,
        "name" -> project.getName,
        "owner" -> project.owner,
        "authors" -> project.authors,
        "channels" -> channelInfo,
        "category" -> Map("title" -> category.title, "icon" -> category.icon),
        "views" -> project.getViews,
        "downloads" -> project.getDownloads,
        "stars" -> project.getStars
      )
    }
  }

  /**
    * Returns a JSON view of all projects.
    *
    * @param version    API version
    * @return           JSON view of projects
    */
  def listProjects(version: String, categories: Option[String], limit: Option[Int], offset: Option[Int]) = Action {
    version match {
      case "v1" =>
        var categoryIds: Array[Int] = null
        if (categories.isDefined) {
          categoryIds = Categories.fromString(categories.get).map(_.id)
        }
        Storage.now(Storage.getProjects(categoryIds, limit.getOrElse(-1), offset.getOrElse(-1))) match {
          case Failure(thrown) => throw thrown
          case Success(projects) => Ok(Json.toJson(projects))
        }
      case zoinks => BadRequest
    }
  }

  def getProject(version: String, pluginId: Option[String]) = Action {
    version match {
      case "v1" => pluginId match {
        case None => NotFound
        case Some(id) => Storage.now(Storage.optProject(id)) match {
          case Failure(thrown) => throw thrown
          case Success(optProject) => optProject match {
            case None => NotFound
            case Some(project) => Ok(Json.toJson(project))
          }
        }
        case yikes => BadRequest
      }
    }
  }

}
