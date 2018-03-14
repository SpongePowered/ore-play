package ore.rest

import java.lang.Math._
import javax.inject.Inject

import db.impl.OrePostgresDriver.api._
import db.impl.access.{ProjectBase, UserBase}
import db.impl.schema.{ProjectSchema, VersionSchema}
import db.{ModelFilter, ModelService}
import models.project.{Page, TagColors}
import models.user.User
import ore.OreConfig
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.json.Json.{obj, arr, toJson}
import util.StringUtils._

/**
  * The Ore API
  */
trait OreRestfulApi {

  val writes: OreWrites
  import writes._

  val service: ModelService
  val config: OreConfig
  val users: UserBase = this.service.getModelBase(classOf[UserBase])

  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  /**
    * Returns a Json value of the Projects meeting the specified criteria.
    *
    * @param categories Project categories
    * @param sort       Ordering
    * @param q          Query string
    * @param limit      Amount to take
    * @param offset     Amount to drop
    * @return           JSON list of projects
    */
  def getProjectList(categories: Option[String], sort: Option[Int], q: Option[String],
                     limit: Option[Int], offset: Option[Int]): JsValue = {
    val queries = this.service.getSchema(classOf[ProjectSchema])
    val categoryArray: Option[Array[Category]] = categories.map(Categories.fromString)
    val ordering = sort.map(ProjectSortingStrategies.withId(_).get).getOrElse(ProjectSortingStrategies.Default)
    val searchFilter = q.map(queries.searchFilter).getOrElse(ModelFilter.Empty)
    val categoryFilter = categoryArray.map(queries.categoryFilter).getOrElse(ModelFilter.Empty)
    val filter = categoryFilter +&& searchFilter
    val maxLoad = this.config.projects.get[Int]("init-load")
    val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)
    val future = queries.collect(filter.fn, ordering, lim, offset.getOrElse(-1))
    val projects = this.service.await(future).get
    toJson(projects)
  }

  /**
    * Returns a Json value of the Project with the specified ID.
    *
    * @param pluginId Project plugin ID
    * @return Json value of project if found, None otherwise
    */
  def getProject(pluginId: String): Option[JsValue] = this.projects.withPluginId(pluginId).map(toJson(_))

  /**
    * Returns a Json value of the Versions meeting the specified criteria.
    *
    * @param pluginId Project plugin ID
    * @param channels Version channels
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         JSON list of versions
    */
  def getVersionList(pluginId: String, channels: Option[String],
                     limit: Option[Int], offset: Option[Int]): Option[JsValue] = {
    this.projects.withPluginId(pluginId).map { project =>
      // Map channel names to IDs
      val channelIds: Option[Seq[Int]] = channels
        .map(_.toLowerCase.split(',')
        .flatMap(name => project.channels.find(equalsIgnoreCase(_.name, name)).map(_.id)).flatten)

      // Only allow versions in the specified channels
      val filter = channelIds.map(service.getSchema(classOf[VersionSchema]).channelFilter).getOrElse(ModelFilter.Empty)
      val maxLoad = this.config.projects.get[Int]("init-version-load")
      val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

      val versions = project.versions.sorted(_.createdAt.desc, filter.fn, lim, offset.getOrElse(-1))
      toJson(versions)
    }
  }

  /**
    * Returns a Json value of the specified version.
    *
    * @param pluginId Project plugin ID
    * @param name     Version name
    * @return         JSON version if found, None otherwise
    */
  def getVersion(pluginId: String, name: String): Option[JsValue] = {
    this.projects.withPluginId(pluginId)
      .flatMap(_.versions.find(equalsIgnoreCase(_.versionString, name)))
      .map(toJson(_))
  }

  /**
    * Returns a list of pages for the specified project.
    *
    * @param pluginId Project plugin ID
    * @param parentId Optional parent ID filter
    * @return         List of project pages
    */
  def getPages(pluginId: String, parentId: Option[Int]): Option[JsValue] = {
    this.projects.withPluginId(pluginId).map { project =>
      val pages = project.pages.sorted(_.name)
      var result: Seq[Page] = null
      if (parentId.isDefined) {
        result = pages.filter(_.parentId == parentId.get)
      } else {
        result = pages.toSeq
      }
      Some(toJson(result.map(page => obj(
        "createdAt" -> page.createdAt,
        "id" -> page.id,
        "name" -> page.name,
        "parentId" -> page.parentId,
        "slug" -> page.slug,
        "fullSlug" -> page.fullSlug
      ))))
    } map {
      toJson(_)
    }
  }

  /**
    * Returns a Json value of Users.
    *
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       List of users
    */
  def getUserList(limit: Option[Int], offset: Option[Int]): JsValue = toJson {
    this.service.await(this.service.collect(
      modelClass = classOf[User],
      limit = limit.getOrElse(-1),
      offset = offset.getOrElse(-1))
    ).get
  }

  /**
    * Returns a Json value of the User with the specified username.
    *
    * @param username Username of User
    * @return         JSON user if found, None otherwise
    */
  def getUser(username: String): Option[JsValue] = this.users.withName(username).map(toJson(_))

  /**
    * Returns a Json array of the tags on a project's version
    *
    * @param pluginId Project plugin ID
    * @param version  Version name
    * @return         Tags on the Version
    */
  def getTags(pluginId: String, version: String): Option[JsValue] = {
    val maybeProject = this.projects.withPluginId(pluginId)
      .flatMap(_.versions.find(equalsIgnoreCase(_.versionString, version)))
    maybeProject match {
      case Some(project) => Some(obj(
        "pluginId" -> pluginId,
        "version" -> version,
        "tags" -> project.tags.map(toJson(_))))
      case None => None
    }
  }

  /**
    * Get the Tag Color information from an ID
    *
    * @param tagId The ID of the Tag Color
    * @return The Tag Color
    */
  def getTagColor(tagId: Int): Option[JsValue] = {
    Some(toJson(TagColors.withId(tagId)))
  }

}

class OreRestfulServer @Inject()(override val writes: OreWrites,
                                 override val service: ModelService,
                                 override val config: OreConfig) extends OreRestfulApi
