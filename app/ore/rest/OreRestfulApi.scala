package ore.rest

import java.lang.Math._
import javax.inject.Inject

import play.api.libs.json.Json.{obj, toJson}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.access.ProjectBase
import db.impl.schema.{
  ChannelTable,
  ProjectRoleTable,
  ProjectStarsTable,
  ProjectTableMain,
  UserTable,
  VersionTable,
  VersionTagTable
}
import db.{Model, DbRef, ModelService}
import models.project._
import models.user.User
import models.user.role.ProjectUserRole
import ore.OreConfig
import ore.permission.role.Role
import ore.project.{Category, ProjectSortingStrategy}

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * The Ore API
  */
trait OreRestfulApi extends OreWrites {

  def service: ModelService
  def config: OreConfig

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
  def getProjectList(
      categories: Option[String],
      sort: Option[Int],
      q: Option[String],
      limit: Option[Long],
      offset: Option[Long]
  ): IO[JsValue] = {
    val cats: Option[Seq[Category]] = categories.map(Category.fromString).map(_.toSeq)
    val ordering                    = sort.map(ProjectSortingStrategy.withValue).getOrElse(ProjectSortingStrategy.Default)

    val maxLoad = this.config.ore.projects.initLoad
    val lim     = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

    def filteredProjects(offset: Option[Long], lim: Long) = {
      val query = queryProjectRV.filter {
        case (p, _, _) =>
          val query = "%" + q.map(_.toLowerCase).getOrElse("") + "%"
          p.name.toLowerCase.like(query) ||
          p.description.toLowerCase.like(query) ||
          p.ownerName.toLowerCase.like(query) ||
          p.pluginId.toLowerCase.like(query)
      }
      //categories.map(_.toSeq).map { cats =>
      val filtered = cats
        .map { ca =>
          query.filter {
            case (p, _, _) =>
              p.category.inSetBind(ca)
          }
        }
        .getOrElse(query)

      filtered
        .sortBy {
          case (p, _, _) =>
            ordering.fn.apply(p)
        }
        .drop(offset.getOrElse(0L))
        .take(lim)
    }

    val query = filteredProjects(offset, lim)

    for {
      projects <- service.runDBIO(query.result)
      json     <- writeProjects(projects)
    } yield {
      toJson(json.map(_._2))
    }
  }

  private def getMembers(projects: Seq[DbRef[Project]]) =
    for {
      r <- TableQuery[ProjectRoleTable] if r.isAccepted === true && r.projectId.inSetBind(projects)
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u)

  def writeMembers(members: Seq[(Model[ProjectUserRole], Model[User])]): Seq[JsObject] = {
    val allRoles = members.groupBy(_._1.userId).mapValues(_.map(_._1.role))
    members.map {
      case (_, user) =>
        val roles                      = allRoles(user.id)
        val trustOrder: Ordering[Role] = Ordering.by(_.permissions: Long) //This is terrible, but probably works
        obj(
          "userId"   -> user.id.value,
          "name"     -> user.name,
          "roles"    -> JsArray(roles.map(role => JsString(role.title))),
          "headRole" -> roles.max(trustOrder).title
        )
    }
  }

  private def writeProjects(
      projects: Seq[(Model[Project], Model[Version], Model[Channel])]
  ): IO[Seq[(Model[Project], JsObject)]] = {
    val projectIds = projects.map(_._1.id.value)
    val versionIds = projects.map(_._2.id.value)

    for {
      chans <- service.runDBIO(queryProjectChannels(projectIds).result).map { chans =>
        chans.groupBy(_.projectId)
      }
      vTags <- service.runDBIO(queryVersionTags(versionIds).result).map { p =>
        p.groupBy(_._1).mapValues(_.map(_._2))
      }
      members <- service.runDBIO(getMembers(projectIds).result).map(_.groupBy(_._1.projectId))
    } yield {

      projects.map {
        case (p, v, c) =>
          (
            p,
            obj(
              "pluginId"    -> p.pluginId,
              "createdAt"   -> p.createdAt.toString,
              "name"        -> p.name,
              "owner"       -> p.ownerName,
              "description" -> p.description,
              "href"        -> ('/' + p.ownerName + '/' + p.slug),
              "members"     -> writeMembers(members.getOrElse(p.id.value, Seq.empty)),
              "channels"    -> toJson(chans.getOrElse(p.id.value, Seq.empty).map(_.obj)),
              "recommended" -> toJson(writeVersion(v, p, c, None, vTags.getOrElse(v.id.value, Seq.empty))),
              "category"    -> obj("title" -> p.category.title, "icon" -> p.category.icon),
              "views"       -> p.viewCount,
              "downloads"   -> p.downloadCount,
              "stars"       -> p.starCount
            )
          )
      }
    }
  }

  def writeVersion(
      v: Model[Version],
      p: Project,
      c: Channel,
      author: Option[String],
      tags: Seq[Model[VersionTag]]
  ): JsObject = {
    val dependencies: List[JsObject] = v.dependencies.map { dependency =>
      obj("pluginId" -> dependency.pluginId, "version" -> dependency.version)
    }
    val json = obj(
      "id"            -> v.id.value,
      "createdAt"     -> v.createdAt.toString,
      "name"          -> v.versionString,
      "dependencies"  -> dependencies,
      "pluginId"      -> p.pluginId,
      "channel"       -> toJson(c),
      "fileSize"      -> v.fileSize,
      "md5"           -> v.hash,
      "staffApproved" -> v.reviewState.isChecked,
      "reviewState"   -> v.reviewState.toString,
      "href"          -> ('/' + v.url(p)),
      "tags"          -> tags.map(toJson(_)),
      "downloads"     -> v.downloadCount,
      "description"   -> v.description
    )

    lazy val jsonVisibility = obj(
      "type" -> v.visibility.nameKey,
      "css"  -> v.visibility.cssClass
    )

    val withVisibility = if (v.visibility == Visibility.Public) json else json + ("visibility" -> jsonVisibility)
    author.fold(withVisibility)(a => withVisibility + (("author", JsString(a))))
  }

  private def queryProjectChannels(projectIds: Seq[DbRef[Project]]) =
    TableQuery[ChannelTable].filter(_.projectId.inSetBind(projectIds))

  private def queryVersionTags(versions: Seq[DbRef[Version]]) =
    for {
      v <- TableQuery[VersionTable] if v.id.inSetBind(versions) && v.visibility === (Visibility.Public: Visibility)
      t <- TableQuery[VersionTagTable] if t.versionId === v.id
    } yield (v.id, t)

  private def queryProjectRV =
    for {
      p <- TableQuery[ProjectTableMain]
      v <- TableQuery[VersionTable] if p.recommendedVersionId === v.id
      c <- TableQuery[ChannelTable] if v.channelId === c.id
      if Visibility.isPublicFilter[ProjectTableMain](p)
    } yield (p, v, c)

  /**
    * Returns a Json value of the Project with the specified ID.
    *
    * @param pluginId Project plugin ID
    * @return Json value of project if found, None otherwise
    */
  def getProject(pluginId: String): IO[Option[JsValue]] = {
    val query = queryProjectRV.filter {
      case (p, _, _) => p.pluginId === pluginId
    }
    for {
      project <- service.runDBIO(query.result.headOption)
      json    <- writeProjects(project.toSeq)
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
  def getVersionList(
      pluginId: String,
      channels: Option[String],
      limit: Option[Int],
      offset: Option[Int],
      onlyPublic: Boolean
  ): IO[JsValue] = {
    val filtered = channels
      .map { chan =>
        queryVersions(onlyPublic).filter {
          case (_, _, _, c, _) =>
            // Only allow versions in the specified channels or all if none specified
            c.name.toLowerCase.inSetBind(chan.toLowerCase.split(","))
        }
      }
      .getOrElse(queryVersions(onlyPublic))
      .filter { case (p, _, _, _, _) => p.pluginId.toLowerCase === pluginId.toLowerCase }
      .sortBy { case (_, v, _, _, _) => v.createdAt.desc }

    val maxLoad = this.config.ore.projects.initVersionLoad
    val lim     = max(min(limit.getOrElse(maxLoad), maxLoad), 0)

    val limited = filtered.drop(offset.getOrElse(0)).take(lim)

    for {
      data  <- service.runDBIO(limited.result) // Get Project Version Channel and AuthorName
      vTags <- service.runDBIO(queryVersionTags(data.map(_._3)).result).map(_.groupBy(_._1).mapValues(_.map(_._2)))
    } yield {
      val list = data.map {
        case (p, v, vId, c, uName) =>
          writeVersion(v, p, c, uName, vTags.getOrElse(vId, Seq.empty))
      }
      toJson(list)
    }
  }

  /**
    * Returns a Json value of the specified version.
    *
    * @param pluginId Project plugin ID
    * @param name     Version name
    * @return         JSON version if found, None otherwise
    */
  def getVersion(pluginId: String, name: String): IO[Option[JsValue]] = {

    val filtered = queryVersions().filter {
      case (p, v, _, _, _) =>
        p.pluginId.toLowerCase === pluginId.toLowerCase &&
          v.versionString.toLowerCase === name.toLowerCase
    }

    for {
      data <- service.runDBIO(filtered.result.headOption)                                     // Get Project Version Channel and AuthorName
      tags <- service.runDBIO(queryVersionTags(data.map(_._3).toSeq).result).map(_.map(_._2)) // Get Tags
    } yield {
      data.map {
        case (p, v, _, c, uName) =>
          writeVersion(v, p, c, uName, tags)
      }
    }
  }

  private def queryVersions(onlyPublic: Boolean = true) =
    for {
      p      <- TableQuery[ProjectTableMain]
      (v, u) <- TableQuery[VersionTable].joinLeft(TableQuery[UserTable]).on(_.authorId === _.id)
      c      <- TableQuery[ChannelTable]
      if v.channelId === c.id && p.id === v.projectId && (if (onlyPublic)
                                                            v.visibility === (Visibility.Public: Visibility)
                                                          else true)
    } yield (p, v, v.id, c, u.map(_.name))

  /**
    * Returns a list of pages for the specified project.
    *
    * @param pluginId Project plugin ID
    * @param parentId Optional parent ID filter
    * @return         List of project pages
    */
  def getPages(
      pluginId: String,
      parentId: Option[DbRef[Page]]
  )(implicit service: ModelService): OptionT[IO, JsValue] = {
    ProjectBase().withPluginId(pluginId).semiflatMap { project =>
      for {
        pages <- service.runDBIO(project.pages(ModelView.raw(Page)).sortBy(_.name).result)
      } yield {
        val seq      = pages.filter(_.parentId == parentId)
        val pageById = pages.map(p => (p.id.value, p)).toMap
        toJson(
          seq.map(
            page =>
              obj(
                "createdAt" -> page.createdAt.value,
                "id"        -> page.id.value,
                "name"      -> page.name,
                "parentId"  -> page.parentId,
                "slug"      -> page.slug,
                "fullSlug"  -> page.fullSlug(page.parentId.flatMap(pageById.get).map(_.obj))
            )
          )
        )
      }
    }
  }

  private def queryStars(users: Seq[Model[User]]) =
    for {
      s <- TableQuery[ProjectStarsTable] if s.userId.inSetBind(users.map(_.id.value))
      p <- TableQuery[ProjectTableMain] if s.projectId === p.id
    } yield (s.userId, p.pluginId)

  /**
    * Returns a Json value of Users.
    *
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       List of users
    */
  def getUserList(
      limit: Option[Int],
      offset: Option[Int]
  )(implicit service: ModelService, cs: ContextShift[IO]): IO[JsValue] =
    for {
      users        <- service.runDBIO(TableQuery[UserTable].drop(offset.getOrElse(0)).take(limit.getOrElse(25)).result)
      writtenUsers <- writeUsers(users)
    } yield toJson(writtenUsers)

  def writeUsers(
      userList: Seq[Model[User]]
  )(implicit service: ModelService, cs: ContextShift[IO]): IO[Seq[JsObject]] = {
    implicit def config: OreConfig = this.config
    import cats.instances.vector._

    val query = queryProjectRV.filter {
      case (p, _, _) => p.userId.inSetBind(userList.map(_.id.value)) // query all projects with given users
    }

    for {
      allProjects     <- service.runDBIO(query.result)
      stars           <- service.runDBIO(queryStars(userList).result).map(_.groupBy(_._1).mapValues(_.map(_._2)))
      jsonProjects    <- writeProjects(allProjects)
      userGlobalRoles <- userList.toVector.parTraverse(_.globalRoles.allFromParent)
    } yield {
      val projectsByUser = jsonProjects.groupBy(_._1.ownerId).mapValues(_.map(_._2))
      userList.zip(userGlobalRoles).map {
        case (user, globalRoles) =>
          obj(
            "id"        -> user.id.value,
            "createdAt" -> user.createdAt.toString,
            "username"  -> user.name,
            "roles"     -> globalRoles.map(_.title),
            "starred"   -> toJson(stars.getOrElse(user.id.value, Seq.empty)),
            "avatarUrl" -> user.avatarUrl,
            "projects"  -> toJson(projectsByUser.getOrElse(user.id.value, Nil))
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
  def getUser(username: String)(implicit service: ModelService, cs: ContextShift[IO]): IO[Option[JsValue]] = {
    val queryOneUser = TableQuery[UserTable].filter {
      _.name.toLowerCase === username.toLowerCase
    }

    for {
      user <- service.runDBIO(queryOneUser.result)
      json <- writeUsers(user)
    } yield json.headOption
  }

  /**
    * Returns a Json array of the tags on a project's version
    *
    * @param pluginId Project plugin ID
    * @param version  Version name
    * @return         Tags on the Version
    */
  def getTags(
      pluginId: String,
      version: String
  )(implicit service: ModelService): OptionT[IO, JsValue] = {
    ProjectBase().withPluginId(pluginId).flatMap { project =>
      project
        .versions(ModelView.now(Version))
        .find(
          v => v.versionString.toLowerCase === version.toLowerCase && v.visibility === (Visibility.Public: Visibility)
        )
        .semiflatMap { v =>
          service.runDBIO(v.tags(ModelView.raw(VersionTag)).result).map { tags =>
            obj("pluginId" -> pluginId, "version" -> version, "tags" -> tags.map(toJson(_))): JsValue
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
  def getTagColor(tagId: Int): Option[JsValue] = TagColor.withValueOpt(tagId).map(toJson(_)(tagColorWrites))
}

class OreRestfulServer @Inject()(val service: ModelService, val config: OreConfig) extends OreRestfulApi
