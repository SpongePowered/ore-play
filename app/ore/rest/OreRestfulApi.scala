package ore.rest

import java.lang.Math._
import javax.inject.Inject
import javax.swing.text.html.parser.TagStack

import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.access.{ProjectBase, UserBase}
import db.impl.schema.{ProjectSchema, ProjectTag, VersionSchema}
import db.{ModelFilter, ModelService}
import models.project._
import models.user.User
import ore.OreConfig
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import play.api.libs.json.{JsObject, JsString, JsValue}
import play.api.libs.json.Json.{obj, toJson}
import com.github.tminglei.slickpg.agg.PgAggFuncSupport.GeneralAggFunctions.arrayAgg
import play.mvc.BodyParser.Json
import slick.lifted.QueryBase
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
    val cats: Option[Seq[Category]] = categories.map(Categories.fromString).map(_.toSeq)
    val ordering = sort.map(ProjectSortingStrategies.withId(_).get).getOrElse(ProjectSortingStrategies.Default)

    val maxLoad = this.config.projects.get[Int]("init-load")
    val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

    def filteredProjects(offset: Option[Int], lim: Int) = {
      val query = queryProjectRV.filter { case (p, v, c) =>
        val query = "%" + q.map(_.toLowerCase).getOrElse("") + "%"
        (p.name.toLowerCase like query) ||
          (p.description.toLowerCase like query) ||
          (p.ownerName.toLowerCase like query) ||
          (p.pluginId.toLowerCase like query)
      }
      //categories.map(_.toSeq).map { cats =>
      val filtered = cats.map { ca =>
        query.filter { case (p, v, c) =>
          p.category inSetBind ca
        }
      } getOrElse query

      filtered sortBy { case (p, v, c) =>
        ordering.fn.apply(p)
      } drop offset.getOrElse(-1) take lim
    }

    val query = filteredProjects(offset, lim)

    val all = for {
      projects <- service.DB.db.run(query.result)
      json <- writeProjects(projects)
    } yield {
      json.map(_._2)
    }
    all.map(toJson(_))
  }

  private def writeProjects(projects: Seq[(Project, Version, Channel)])(implicit ec: ExecutionContext): Future[Seq[(Project, JsObject)]] = {
    val projectIds = projects.flatMap(_._1.id)
    val versionIds = projects.flatMap(_._2.id)

    for {
      chans <- service.DB.db.run(queryProjectChannels(projectIds).result).map { chans => chans.groupBy(_.projectId) }
      vTags <- service.DB.db.run(queryVersionTags(versionIds).result).map { p => p.groupBy(_._1) mapValues (_.map(_._2)) }
    } yield {
      projects.map { case (p, v, c) =>
        (p, obj(
          "pluginId" -> p.pluginId,
          "createdAt" -> p.createdAt.get.toString,
          "name" -> p.name,
          "owner" -> p.ownerName,
          "description" -> p.description,
          "href" -> ('/' + p.ownerName + '/' + p.slug),
          //"members"       ->  p.memberships.members.filter(_.roles.exists(_.isAccepted)), // TODO members
          "channels"      ->  toJson(chans.getOrElse(p.id.get, Seq.empty)),
          "recommended"   ->  toJson(writeVersion(v, p, c, None, vTags.getOrElse(v.id.get, Seq.empty))),    // TODO channel(one) and tags
          "category" -> obj("title" -> p.category.title, "icon" -> p.category.icon),
          "views" -> p.viewCount,
          "downloads" -> p.downloadCount,
          "stars" -> p.starCount
        ))
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
    author.fold(json)(a => json + (("author", JsString(a))))
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

  private def queryProjectRV = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]
    val tableChannels = TableQuery[ChannelTable]

    val allProjects = for {
      p <- tableProject
      v <- tableVersion if p.recommendedVersionId === v.id
      c <- tableChannels if v.channelId === c.id
    } yield {
      (p, v, c)
    }

    allProjects.filter { case (p, v, c) =>
      p.visibility =!= VisibilityTypes.SoftDelete &&
      p.visibility =!= VisibilityTypes.NeedsChanges
    }
  }

  /**
    * Returns a Json value of the Project with the specified ID.
    *
    * @param pluginId Project plugin ID
    * @return Json value of project if found, None otherwise
    */
  def getProject(pluginId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    val query = queryProjectRV.filter {
      case (p, v, c) => p.pluginId === pluginId
    }
    for {
      project <- service.DB.db.run(query.result.headOption)
      json <- writeProjects(project.toSeq)
    } yield {
      json.headOption.map(_._2)
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

    val filtered = channels.map { chan =>
      queryVersions.filter { case (p, v, vId, c, uName) =>
          // Only allow versions in the specified channels or all if none specified
          c.name inSetBind chan.toLowerCase.split(",")
      }
    } getOrElse queryVersions filter {
      case (p, v, vId, c, uName) =>
        p.pluginId.toLowerCase === pluginId.toLowerCase
    }


    // val grouped = filtered.groupBy(_._1.*)
    // TODO grouped type is nothing for some reason :/ grouping in memory instead below

    val maxLoad = this.config.projects.get[Int]("init-version-load")
    val lim = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

    val limited = filtered.drop(offset.getOrElse(-1)).take(lim)

    for {
      data <- service.DB.db.run(limited.result) // Get Project Version Channel and AuthorName
      vTags <- service.DB.db.run(queryVersionTags(data.map(_._3)).result).map { p => p.groupBy(_._1) mapValues (_.map(_._2)) }
    } yield {
      val list = data.map { case (p, v, vId, c, uName) =>
        writeVersion(v, p, c, Some(uName), vTags.getOrElse(vId, Seq.empty))
      }
      Some(toJson(list))
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

    val filtered = queryVersions.filter { case (p, v, vId, c, uName) =>
      p.pluginId.toLowerCase === pluginId.toLowerCase &&
      v.versionString.toLowerCase === name.toLowerCase
    }
    // val grouped = filtered.groupBy(_._1.*)
    // TODO grouped type is nothing for some reason :/

    for {
      data <- service.DB.db.run(filtered.result.headOption) // Get Project Version Channel and AuthorName
      tags <- service.DB.db.run(queryVersionTags(data.map(_._3).toSeq).result).map(_.map(_._2)) // Get Tags
    } yield {
      data.map { case (p, v, _, c, uName) =>
        writeVersion(v, p, c, Some(uName), tags)
      }
    }
  }


  private def queryVersions: Query[(ProjectTableMain, VersionTable, Rep[Int], ChannelTable, Rep[String]), (Project, Version, Int, Channel, String), Seq] = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]
    val tableChannels = TableQuery[ChannelTable]
    val tableUsers = TableQuery[UserTable]

    for {
      p <- tableProject
      v <- tableVersion if p.id === v.projectId
      c <- tableChannels if v.channelId === c.id
      u <- tableUsers if v.authorId === u.id
    } yield {
      (p, v, v.id, c, u.name)
    }
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
      case None => Future.successful(None)
      case Some(project) =>
        for {
          pages <- project.pages.sorted(_.name)
        } yield {
          val seq = if (parentId.isDefined) pages.filter(_.parentId == parentId.get) else pages
          val pageById = seq.map(p => (p.id.get, p)).toMap
          Some(toJson(seq.map(page => obj(
            "createdAt" -> page.createdAt,
            "id" -> page.id,
            "name" -> page.name,
            "parentId" -> page.parentId,
            "slug" -> page.slug,
            "fullSlug" -> page.fullSlug(pageById.get(page.parentId))
          ))))
        }
    }
  }

  private def queryUser = {
    val tableUsers = TableQuery[UserTable]
    val tableStars = TableQuery[ProjectStarsTable]
    val tableProject = TableQuery[ProjectTableMain]

    val baseQuery = for {
      u <- tableUsers
      s <- tableStars if u.id === s.userId
      p <- tableProject if s.projectId === p.id
    } yield {
      (u, p.pluginId) // user and starred plugin ids
    }
    // TODO get groupby user to work if possible
    baseQuery
  }

  /**
    * Returns a Json value of Users.
    *
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       List of users
    */
  def getUserList(limit: Option[Int], offset: Option[Int])(implicit ec: ExecutionContext): Future[JsValue] = {
    service.DB.db.run(queryUser.drop(offset.getOrElse(-1)).take(limit.getOrElse(-1)).result).map { l =>
      l.groupBy(_._1).mapValues(_.map(_._2)).toSeq // grouping in memory instead
    } flatMap { l =>
      writeUsers(l)
    } map {
      toJson(_)
    }
  }

  def writeUsers(userList: Seq[(User, Seq[String])])(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {

    val query = queryProjectRV.filter {
      case (p, v, c) => p.userId inSetBind userList.flatMap(_._1.id) // query all projects with given users
    }

    service.DB.db.run(query.result).flatMap { allProjects =>
      writeProjects(allProjects)
    } map { jsonProjects =>
      jsonProjects.groupBy(_._1.ownerId).mapValues(_.map(_._2))
    } map { projectsByUser =>
      userList.map { case (user, starred) =>
        obj(
          "id"              ->  user.id,
          "createdAt"       ->  user.createdAt.get.toString,
          "username"        ->  user.username,
          "roles"           ->  user.globalRoles.map(_.title),
          "starred"         ->  toJson(starred),
          "avatarUrl"       ->  user.avatarUrl,
          "projects"        ->  toJson(projectsByUser.get(user.id.get))
        )
      }
    }
  }

  /**
    * Returns a Json value of the User with the specified username.
    *
    * @param username Username of User
    * @return         JSON user if found, None otherwise
    */
  def getUser(username: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    val queryOneUser = queryUser.filter { case (user, _) =>
      user.name.toLowerCase === username.toLowerCase
    }

    service.DB.db.run(queryOneUser.result).map { l =>
      l.groupBy(_._1).mapValues(_.map(_._2)).toSeq // grouping in memory instead
    } flatMap { l =>
      writeUsers(l)
    } map {
      _.headOption
    }
  }

  /**
    * Returns a Json array of the tags on a project's version
    *
    * @param pluginId Project plugin ID
    * @param version  Version name
    * @return         Tags on the Version
    */
  def getTags(pluginId: String, version: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    this.projects.withPluginId(pluginId).flatMap {
      case None => Future.successful(None)
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
