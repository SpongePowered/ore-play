package models.project

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import com.google.common.base.Preconditions._
import db.Storage
import models.auth.User
import models.author.Dev
import models.project.Project._
import models.project.Categories.Category
import models.project.ChannelColors.ChannelColor
import models.project.Version.PendingVersion
import org.apache.commons.io.FileUtils
import org.spongepowered.plugin.meta.PluginMetadata
import play.api.Play.current
import play.api.cache.Cache
import plugin.{Pages, PluginFile, ProjectManager}
import util.{Cacheable, PendingAction}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success, Try}

/**
  * Represents an Ore package.
  *
  * <p>Note: As a general rule, do not handle actions / results in model classes</p>
  *
  * <p>Note: Instance variables should be private unless they are database
  * properties unless they are vars instead of vals in which case accessors
  * and mutators must be used.</p>
  *
  * @param id                     Unique identifier
  * @param createdAt              Instant of creation
  * @param pluginId               Plugin ID
  * @param name                   Name of plugin
  * @param owner                  The owner Author for this project
  * @param authors                Authors who work on this project
  * @param homepage               The external project URL
  * @param recommendedVersionId   The ID of this project's recommended version
  * @param categoryId             The ID of this project's category
  * @param views                  How many times this project has been views
  * @param downloads              How many times this project has been downloaded in total
  * @param starred                How many times this project has been starred
  */
case class Project(id: Option[Int], private var createdAt: Option[Timestamp], pluginId: String,
                   private var name: String, private var slug: String, owner: String,
                   authors: List[String], homepage: Option[String],
                   private var recommendedVersionId: Option[Int],
                   private var categoryId: Int = -1, private var views: Int,
                   private var downloads: Int, private var starred: Int) {

  private lazy val dateFormat = new SimpleDateFormat("MM-dd-yyyy")

  def this(pluginId: String, name: String, owner: String, authors: List[String], homepage: String) = {
    this(None, None, pluginId, sanitizeName(name), slugify(name), owner, authors, Option(homepage), None, 0, 0, 0, 0)
  }

  def getOwner: Dev = Dev(owner) // TODO: Teams

  def getAuthors: List[Dev] = for (author <- authors) yield Dev(author) // TODO: Teams

  /**
    * Returns the Timestamp instant that this Project was created or None if it
    * has not yet been created.
    *
    * @return Instant of creation or None if has not been created
    */
  def getCreatedAt: Option[Timestamp] = this.createdAt

  /**
    * Method called when this Project is created in the database.
    */
  def onCreate() = this.createdAt = Some(new Timestamp(new Date().getTime))

  /**
    * Returns the name of this project.
    *
    * @return Name of project
    */
  def getName: String = this.name

  /**
    * Returns this Project's URL slug.
    *
    * @return URL slug
    */
  def getSlug: String = this.slug

  /**
    * Sets the name of this project and performs all the necessary renames.
    *
    * @param name   New name
    * @return       Future result
    */
  def setName(name: String) = {
    val newName = sanitizeName(name)
    checkArgument(Project.isNamespaceAvailable(this.owner, newName), "slug not available", "")
    checkArgument(isValidName(newName), "invalid name", "")
    Storage.now(Storage.updateProjectString(this, _.name, newName)) match {
      case Failure(thrown) => throw thrown
      case Success(i) =>
        ProjectManager.renameProject(this.owner, this.name, newName)
        this.name = newName
        Storage.now(Storage.updateProjectString(this, _.slug, slugify(this))) match {
          case Failure(thrown) => throw thrown
          case Success(j) =>
            this.slug = slugify(this)
        }
    }
  }

  /**
    * Returns all Channels belonging to this Project.
    *
    * @return All channels in project
    */
  def getChannels: Future[Seq[Channel]] = Storage.getChannels(this.id.get)

  /**
    * Returns the Channel in this project with the specified name.
    *
    * @param name   Name of channel
    * @return       Channel with name, if present, None otherwise
    */
  def getChannel(name: String): Future[Option[Channel]] = Storage.optChannel(this.id.get, name)

  /**
    * Returns the Channel in this project with the specified color. Colors are
    * unique to Channels within Projects.
    *
    * @param color  Color of channel
    * @return       Channel with color, if present, None otherwise
    */
  def getChannel(color: ChannelColor): Future[Option[Channel]] = Storage.optChannel(this.id.get, color.id)

  /**
    * Creates a new Channel for this project with the specified name.
    *
    * @param name   Name of channel
    * @return       New channel
    */
  def newChannel(name: String, color: ChannelColor): Try[Channel] = Try {
    checkArgument(Channel.isValidName(name), "invalid name", "")
    Storage.now(getChannels) match {
      case Failure(thrown) => throw thrown
      case Success(channels) => if (channels.size >= Channel.MAX_AMOUNT) {
        throw new IllegalArgumentException("Project has reached maximum channel capacity.")
      }
    }
    Storage.now(Storage.createChannel(new Channel(name, color, this.id.get))) match {
      case Failure(thrown) => throw thrown
      case Success(channel) => channel
    }
  }

  /**
    * Returns this Project's recommended version.
    *
    * @return Recommended version
    */
  def getRecommendedVersion: Future[Version] = Storage.getVersion(this.recommendedVersionId.get)

  /**
    * Updates this project's recommended version.
    *
    * @param version  Version to set
    * @return         Result
    */
  def setRecommendedVersion(version: Version) = {
    Storage.updateProjectInt(this, _.recommendedVersionId, version.id.get).onSuccess {
      case i => this.recommendedVersionId = version.id
    }
  }

  /**
    * Returns all Versions belonging to this Project.
    *
    * @return All versions in project
    */
  def getVersions: Seq[Version] = Storage.now(Storage.getAllVersions(this.id.get)) match {
    case Failure(thrown) =>  throw thrown
    case Success(versions) => versions
  }

  /**
    * Returns all Versions belonging to the specified channels.
    *
    * @param channels   Channels to get versions for
    * @return           All versions in channels
    */
  def getVersions(channels: Seq[Channel]): Seq[Version] = {
    channels.foreach(c => checkArgument(c.projectId == this.id.get, "channel doesn't belong to project", ""))
    Storage.now(Storage.getVersions(this.id.get, channels.map(_.id.get))) match {
      case Failure(thrown) => throw thrown
      case Success(versions) => versions
    }
  }

  /**
    * Returns this Project's category.
    *
    * @return Project category
    */
  def getCategory: Category = Categories(this.categoryId)

  /**
    * Sets this Project's category.
    *
    * @param category Category to set
    */
  def setCategory(category: Category) = {
    if (exists) {
      Storage.now(Storage.updateProjectInt(this, _.categoryId, category.id)) match {
        case Failure(thrown) => throw thrown
        case Success(i) => ;
      }
    }
    this.categoryId = category.id
  }

  /**
    * Returns the amount of unique views on this Project.
    *
    * @return Unique views on project
    */
  def getViews: Int = this.views

  /**
    * Increments this Project's view count by one.
    *
    * @return Future result
    */
  def addView(): Future[Int] = {
    val f = Storage.updateProjectInt(this, _.views, this.views + 1)
    f.onSuccess {
      case i => this.views += 1
    }
    f
  }

  /**
    * Returns the amount of unique downloads this Project has.
    *
    * @return Amount of unique downloads
    */
  def getDownloads: Int = this.downloads

  /**
    * Increments this Project's downloadc count by one.
    *
    * @return Future result
    */
  def addDownload(): Future[Int] = {
    val f = Storage.updateProjectInt(this, _.downloads, this.downloads + 1)
    f.onSuccess {
      case i => this.downloads += 1
    }
    f
  }

  /**
    * Returns the amount of times this Project has been starred.
    *
    * @return Amount of stars
    */
  def getStars: Int = this.starred

  /**
    * Returns true if this Project is starred by the specified User.
    *
    * @param user   User to check if starred for
    * @return       True if starred by User
    */
  def isStarredBy(user: User): Boolean = {
    Storage.now(Storage.isProjectStarredBy(this, user)) match {
      case Failure(thrown) => throw thrown
      case Success(isStarred) => isStarred
    }
  }

  /**
    * Returns true if this Project is starred by the User with the specified
    * username.
    *
    * @param username   To get User of
    * @return           True if starred by User
    */
  def isStarredBy(username: String): Boolean = {
    Storage.now(Storage.getUser(username)) match {
      case Failure(thrown) => throw thrown
      case Success(user) => isStarredBy(user)
    }
  }

  /**
    * Sets this Project as starred for the specified User.
    *
    * @param user   User to star for
    * @return       Future result
    */
  def starFor(user: User): Future[Int] = {
    val f = Storage.starProjectFor(this, user)
    f.onSuccess {
      case i =>
        Storage.updateProjectInt(this, _.starred, this.starred + 1).onSuccess {
          case j => this.starred += 1
        }
    }
    f
  }

  /**
    * Removes a star for this Project for the specified User.
    *
    * @param user   User to unstar for
    * @return       Future result
    */
  def unstarFor(user: User): Future[Int] = {
    val f = Storage.unstarProjectFor(this, user)
    f.onSuccess {
      case i =>
        Storage.updateProjectInt(this, _.starred, this.starred - 1).onSuccess {
          case j => this.starred -= 1
        }
    }
    f
  }

  /**
    * Returns true if this Project already exists.
    *
    * @return True if project exists, false otherwise
    */
  def exists: Boolean = {
    Storage.now(Storage.isDefined(Storage.getProject(this.owner, this.name))).isSuccess
  }

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable: Boolean = Project.isNamespaceAvailable(this.owner, this.slug)

  /**
    * Immediately deletes this projects and any associated files.
    *
    * @return Result
    */
  def delete: Try[Unit] = Try {
    Storage.now(Storage.deleteProject(this)) match {
      case Failure(thrown) => throw thrown
      case Success(i) =>
        FileUtils.deleteDirectory(ProjectManager.getProjectDir(this.owner, this.name).toFile)
        FileUtils.deleteDirectory(Pages.getDocsDir(this.owner, this.name).toFile)
    }
  }

  /**
    * Returns a presentable date string of this version's creation date.
    *
    * @return Creation date string
    */
  def prettyDate: String = {
    this.dateFormat.format(this.createdAt.get)
  }

  override def hashCode: Int = this.id.get.hashCode

  override def equals(o: Any): Boolean = {
    o.isInstanceOf[Project] && o.asInstanceOf[Project].id.get == this.id.get
  }

}

object Project {

  /**
    * The maximum length for a Project name.
    */
  val MAX_NAME_LENGTH: Int = 25

  /**
    * Represents a Project with an uploaded plugin that has not yet been
    * created.
    *
    * @param project        Pending project
    * @param firstVersion   Uploaded plugin
    */
  case class PendingProject(project: Project, firstVersion: PluginFile) extends PendingAction[Project] with Cacheable {

    private var pendingVersion: Option[PendingVersion] = None

    /**
      * Creates a new PendingVersion for this PendingProject
      *
      * @return New PendingVersion
      */
    def initFirstVersion: PendingVersion = {
      val meta = this.firstVersion.getMeta.get
      val version = Version.fromMeta(this.project, meta)
      val pending = Version.setPending(project.owner, project.slug,
        Channel.getSuggestedNameForVersion(version.versionString), version, this.firstVersion)
      this.pendingVersion = Some(pending)
      pending
    }

    /**
      * Returns this PendingProject's PendingVersion
      *
      * @return PendingVersion
      */
    def getPendingVersion: Option[PendingVersion] = this.pendingVersion

    override def complete: Try[Project] = Try {
      free()
      ProjectManager.createProject(this) match {
        case Failure(thrown) => throw thrown
        case Success(newProject) => ProjectManager.createVersion(this.pendingVersion.get) match {
          case Failure(thrown) => throw thrown
          case Success(newVersion) =>
            newProject.setRecommendedVersion(newVersion)
            newProject
        }
      }
    }

    override def cancel() = {
      free()
      this.firstVersion.delete()
      if (project.exists) {
        project.delete
      }
    }

    override def getKey: String = this.project.owner + '/' + this.project.slug

  }

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String): Boolean = {
    Storage.now(Storage.optProjectOfSlug(owner, slug)) match {
      case Failure(thrown) => throw thrown
      case Success(optProject) => optProject.isEmpty
    }
  }

  def isValidName(name: String): Boolean = {
    val sanitized = sanitizeName(name)
    sanitized.length >= 1 && sanitized.length <= MAX_NAME_LENGTH
  }

  def sanitizeName(name: String): String = name.trim.replaceAll(" +", " ")

  /**
    * Returns a URL slug that should be used for the project.
    *
    * @return URL slug
    */
  def slugify(project: Project): String = slugify(project.getName)

  /**
    * Returns a URL slug that should be used for the project.
    *
    * @return URL slug
    */
  def slugify(name: String) = sanitizeName(name).replace(' ', '-')

  /**
    * Marks the specified Project as pending for later use.
    *
    * @param project        Project that is pending
    * @param firstVersion   Uploaded plugin
    */
  def setPending(project: Project, firstVersion: PluginFile) =  {
    PendingProject(project, firstVersion).cache()
  }

  /**
    * Returns the PendingProject of the specified owner and name, if any.
    *
    * @param owner  Project owner
    * @param slug   Project slug
    * @return       PendingProject if present, None otherwise
    */
  def getPending(owner: String, slug: String): Option[PendingProject] = {
    Cache.getAs[PendingProject](owner + '/' + slug)
  }

  /**
    * Creates a new Project from the specified PluginMetadata.
    *
    * @param owner  Owner of project
    * @param meta   PluginMetadata object
    * @return       New project
    */
  def fromMeta(owner: String, meta: PluginMetadata): Project = {
    new Project(meta.getId, meta.getName, owner, meta.getAuthors.toList, meta.getUrl)
  }

}
