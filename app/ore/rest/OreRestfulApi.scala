package ore.rest

import java.lang.Math._
import javax.inject.Inject
import javax.swing.text.html.parser.TagStack

import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.access.{ProjectBase, UserBase}
import db.impl.schema.{ProjectSchema, ProjectTag, VersionSchema}
import db.{ModelFilter, ModelService}
import models.project.{Channel, Project, TagColors, Version}
import models.user.User
import ore.OreConfig
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import play.api.libs.json.{JsObject, JsString, JsValue}
import play.api.libs.json.Json.{obj, toJson}
import com.github.tminglei.slickpg.agg.PgAggFuncSupport.GeneralAggFunctions.arrayAgg
import play.mvc.BodyParser.Json
import util.StringUtils._

import scala.concurrent.{ExecutionContext, Future}

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
                     limit: Option[Int], offset: Option[Int])(implicit ec: ExecutionContext): Future[JsValue] = {
    val queries = this.service.getSchema(classOf[ProjectSchema])
    val categoryArray: Option[Array[Category]] = categories.map(Categories.fromString)
    val ordering = sort.map(ProjectSortingStrategies.withId(_).get).getOrElse(ProjectSortingStrategies.Default)

    val maxLoad = this.config.projects.get[Int]("init-load")
    val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

    def filteredProjects(offset: Option[Int], lim: Int) = {
      val query = queryProject.filter { case (p, v, c) =>
        val query = "%" + q.map(_.toLowerCase).getOrElse("") + "%"
        (p.name.toLowerCase like query) ||
          (p.description.toLowerCase like query) ||
          (p.ownerName.toLowerCase like query) ||
          (p.pluginId.toLowerCase like query)
      }
      query.filter { case (p, v, c) =>
        categoryArray.map {
          p.category inSetBind _
        } getOrElse true
      } sortBy { case (p, v, c) =>
        ordering
      } drop offset.getOrElse(-1) take lim
    }

    val query = filteredProjects(offset, lim)

    val all = for {
      projects <- service.DB.db.run(query.result)
      json <- writeProjects(projects)
    } yield {
      json
    }
    all.map(toJson(_))
  }




  private def writeProjects(projects: Seq[(Project, Version, Channel)]) = {
    val projectIds = projects.flatMap(_._1.id)
    val versionIds = projects.flatMap(_._2.id)

    for {
      chans <- service.DB.db.run(queryProjectChannels(projectIds).result).map { chans => chans.groupBy(_.projectId) }
      vTags <- service.DB.db.run(queryVersionTags(versionIds).result).map { p => p.groupBy(_._1) mapValues (_.map(_._2)) }
    } yield {
      projects.map { case (p, v, c) =>
        obj(
          "pluginId" -> p.pluginId,
          "createdAt" -> p.createdAt.get.toString,
          "name" -> p.name,
          "owner" -> p.ownerName,
          "description" -> p.description,
          "href" -> ('/' + p.ownerName + '/' + p.slug),
          //"members"       ->  p.memberships.members.filter(_.roles.exists(_.isAccepted)), // TODO members
          "channels"      ->  toJson(chans.getOrElse(p.id.get, Seq.empty)),
          "recommended"   ->  toJson(writeVersion(v, p, c, None, vTags.getOrElse(v.id, Seq.empty))),                                                  // TODO channel(one) and tags
          "category" -> obj("title" -> p.category.title, "icon" -> p.category.icon),
          "views" -> p.viewCount,
          "downloads" -> p.downloadCount,
          "stars" -> p.starCount
        )
      }
    }
  }

  def writeVersion(v: Version, p: Project, c: Channel, author: Option[String], tags: Seq[ProjectTag]): JsObject = {
    val dependencies: List[JsObject] = v.dependencies.map { dependency =>
      obj("pluginId" -> dependency.pluginId, "version" -> dependency.version)
    }
    val json = obj(
      "id"            ->  v.id.get,
      "createdAt"     ->  v.createdAt.get.toString,
      "name"          ->  v.versionString,
      "dependencies"  ->  dependencies,
      "pluginId"      ->  p.pluginId,
      "channel"       ->  toJson(c),
      "fileSize"      ->  v.fileSize,
      "md5"           ->  v.hash,
      "staffApproved" ->  v.isReviewed,
      "href"          ->  ('/' + v.url(p)),
      "tags"          ->  tags.map(toJson(_)),
      "downloads"     ->  v.downloadCount
    )
    author.map(a => json.+(("author", JsString(a)))).getOrElse(json)
  }

  private def queryProjectChannels(projectIds: Seq[Int]) = {
    val tableChannels = TableQuery[ChannelTable]
    for {
      c <- tableChannels if c.projectId inSetBind projectIds
    } yield {
      c
    }
  }

  private def queryVersionTags(versions: Seq[Int]) = {
    val tableTags = TableQuery[TagTable]
    val tableVersion = TableQuery[VersionTable]
    for {
      v <- tableVersion if v.id inSetBind versions
      t <- tableTags if t.id === v.tagIds.any // TODO check if this is correct
    } yield {
      (v.id, t)
    }
  }

  private def queryProject = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]
    val tableChannels = TableQuery[ChannelTable]

    for {
      p <- tableProject
      v <- tableVersion if p.recommendedVersionId === v.id
      c <- tableChannels if v.channelId === c.id
    } yield {
      (p, v, c)
    }
  }

  /**
    * Returns a Json value of the Project with the specified ID.
    *
    * @param pluginId Project plugin ID
    * @return Json value of project if found, None otherwise
    */
  def getProject(pluginId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    val query = queryProject.filter {
      case (p, v, c) => p.pluginId
    }
    for {
      project <- service.DB.db.run(query.result.headOption)
      json <- writeProjects(project.toSeq)
    } yield {
      json.headOption
    }
  }

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
                     limit: Option[Int], offset: Option[Int])(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    this.projects.withPluginId(pluginId).flatMap {
      case None => Future(None)
      case Some(project) =>
        val filter = channels match {
          case None => Future(ModelFilter.Empty)
          case Some(c) =>
            // Map channel names to IDs
          project.channels.filter(_.name inSet c.toLowerCase().split(",")).map(_.map(_.id.getOrElse(-1)))
          // Only allow versions in the specified channels
            .map(service.getSchema(classOf[VersionSchema]).channelFilter(_))
        }
        val maxLoad = this.config.projects.get[Int]("init-version-load")
        val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)
        filter.flatMap { f =>
          project.versions.sorted(_.createdAt.desc, f.fn, lim, offset.getOrElse(-1)).map(toJson(_)).map(Some(_))
        }
    }
  }

  /**
    * Returns a Json value of the specified version.
    *
    * @param pluginId Project plugin ID
    * @param name     Version name
    * @return         JSON version if found, None otherwise
    */
  def getVersion(pluginId: String, name: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    this.projects.withPluginId(pluginId).flatMap {
      case None => Future(None)
      case Some(p) => p.versions.find(equalsIgnoreCase(_.versionString, name))
    }.map(_.map(toJson(_)))
  }

  /**
    * Returns a list of pages for the specified project.
    *
    * @param pluginId Project plugin ID
    * @param parentId Optional parent ID filter
    * @return         List of project pages
    */
  def getPages(pluginId: String, parentId: Option[Int])(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    this.projects.withPluginId(pluginId).flatMap {
      case None => Future(None)
      case Some(project) =>
        val pages = project.pages.sorted(_.name)
        val result = if (parentId.isDefined) pages.map(_.filter(_.parentId == parentId.get)) else pages
        result.map(seq =>
          Some(toJson(seq.map(page => obj(
            "createdAt" -> page.createdAt,
            "id" -> page.id,
            "name" -> page.name,
            "parentId" -> page.parentId,
            "slug" -> page.slug,
            "fullSlug" -> page.fullSlug
          ))))
        )
    }
  }

  /**
    * Returns a Json value of Users.
    *
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       List of users
    */
  def getUserList(limit: Option[Int], offset: Option[Int])(implicit ec: ExecutionContext): Future[JsValue] = {
    val userList = this.service.collect(modelClass = classOf[User], limit = limit.getOrElse(-1), offset = offset.getOrElse(-1))
    userList.map(toJson(_))
  }


  /**
    * Returns a Json value of the User with the specified username.
    *
    * @param username Username of User
    * @return         JSON user if found, None otherwise
    */
  def getUser(username: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = this.users.withName(username).map(_.map(toJson(_)))

  /**
    * Returns a Json array of the tags on a project's version
    *
    * @param pluginId Project plugin ID
    * @param version  Version name
    * @return         Tags on the Version
    */
  def getTags(pluginId: String, version: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    this.projects.withPluginId(pluginId).flatMap {
      case None => Future(None)
      case Some(project) => project.versions.find(equalsIgnoreCase(_.versionString, version)).flatMap {
        case None => Future.successful(None)
        case Some(v) =>
          v.tags.map { tags =>
            Some(obj(
              "pluginId" -> pluginId,
              "version" -> version,
              "tags" -> tags.map(toJson(_))))
          }
      }
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
