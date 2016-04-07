package models.project

import java.sql.Timestamp
import java.text.SimpleDateFormat

import db.Storage
import models.project.ChannelColors.ChannelColor
import org.spongepowered.plugin.meta.PluginMetadata
import play.api.Play.current
import play.api.cache.Cache
import plugin.{PluginFile, ProjectManager}
import util.{Cacheable, PendingAction}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/**
  * Represents a single version of a Project.
  *
  * @param id             Unique identifier
  * @param createdAt      Instant of creation
  * @param versionString  Version string
  * @param dependencies   List of plugin dependencies with the plugin ID and
  *                       version separated by a ':'
  * @param description    User description of version
  * @param assets         Path to assets directory within plugin
  * @param downloads      The amount of times this version has been downloaded
  * @param projectId      ID of project this version belongs to
  * @param channelId      ID of channel this version belongs to
  */
case class Version(id: Option[Int], var createdAt: Option[Timestamp], versionString: String,
                   dependencies: List[String], private var description: Option[String],
                   assets: Option[String], private var downloads: Int, projectId: Int,
                   var channelId: Int) {

  private lazy val dateFormat = new SimpleDateFormat("MM-dd-yyyy")

  def this(versionString: String, dependencies: List[String], description: String,
           assets: String, projectId: Int, channelId: Int) = {
    this(None, None, versionString, dependencies, Option(description), Option(assets), 0, projectId, channelId)
  }

  def this(versionString: String, dependencies: List[String],
           description: String, assets: String, projectId: Int) = {
    this(versionString, dependencies, description, assets, projectId, -1)
  }

  /**
    * Returns the project this version belongs to.
    *
    * @return Project
    */
  def getProject: Future[Project] = Storage.getProject(this.projectId)

  /**
    * Returns the channel this version belongs to.
    *
    * @return Channel
    */
  def getChannel: Future[Channel] = Storage.getChannel(this.channelId)

  /**
    * Returns the channel this version belongs to from the specified collection
    * of channels if present.
    *
    * @param channels   Channels to search
    * @return           Channel if present, None otherwise
    */
  def getChannelFrom(channels: Seq[Channel]): Option[Channel] = channels.find(_.id.get == this.channelId)

  /**
    * Returns this Version's description.
    *
    * @return Version description
    */
  def getDescription: Option[String] = this.description

  /**
    * Sets this Version's description.
    *
    * @param description Version description
    */
  def setDescription(description: String): Future[Int] = {
    val f = Storage.updateVersionString(this, _.description, description)
    f.onComplete {
      case i => this.description = Some(description)
    }
    f
  }

  /**
    * Returns this Versions plugin dependencies.
    *
    * @return Plugin dependencies
    */
  def getDependencies: List[Dependency] = {
    for (depend <- this.dependencies) yield {
      val data = depend.split(":")
      Dependency(data(0), data(1))
    }
  }

  /**
    * Returns the amount of unique downloads this Version has.
    *
    * @return Amount of unique downloads
    */
  def getDownloads: Int = this.downloads

  /**
    * Increments this Version's download count by one.
    *
    * @return Future result
    */
  def addDownload(): Future[Int] = {
    val f = Storage.updateVersionInt(this, _.downloads, this.downloads + 1)
    f.onSuccess {
      case i => this.downloads += 1;
    }
    f
  }

  /**
    * Returns a presentable date string of this version's creation date.
    *
    * @return Creation date string
    */
  def prettyDate: String = {
    this.dateFormat.format(this.createdAt.get)
  }

  override def hashCode: Int = this.id.hashCode

  override def equals(o: Any): Boolean = {
    o.isInstanceOf[Version] && o.asInstanceOf[Version].id.get == this.id.get
  }

}

object Version {

  /**
    * Represents a pending version to be created later.
    *
    * @param owner          Name of project owner
    * @param projectSlug    Project slug
    * @param channelName    Name of channel this version will be in
    * @param channelColor   Color of channel for this version
    * @param version        Version that is pending
    * @param plugin         Uploaded plugin
    */
  case class PendingVersion(owner: String, projectSlug: String, private var channelName: String,
                            private var channelColor: ChannelColor, version: Version,
                            plugin: PluginFile) extends PendingAction[Version] with Cacheable {

    /**
      * Returns the Channel name that this Version will be assigned to or created
      * if it does not exist.
      *
      * @return Pending channel name
      */
    def getChannelName: String = this.channelName

    /**
      * Sets the Channel name that this Version will be assigned to or created
      * if it does not exist.
      *
      * @param channelName Pending channel name
      */
    def setChannelName(channelName: String) = this.channelName = channelName

    /**
      * Returns the Channel color to use if a Channel needs to be created to
      * create this Version.
      *
      * @return Channel color to user
      */
    def getChannelColor: ChannelColor = this.channelColor

    /**
      * Sets the Channel color to use if a Channel needs to be created to
      * create this Version.
      *
      * @param channelColor Channel color to user
      */
    def setChannelColor(channelColor: ChannelColor) = this.channelColor = channelColor

    override def complete: Try[Version] = Try {
      free()
      return ProjectManager.createVersion(this)
    }
    
    override def cancel() = {
      free()
      this.plugin.delete()
    }

    override def getKey: String = {
      this.owner + '/' + this.projectSlug + '/' + this.channelName + '/' + this.version.versionString
    }

  }

  /**
    * Marks the specified Version as pending and caches it for later use.
    *
    * @param owner    Name of owner
    * @param slug     Project slug
    * @param channel  Name of channel
    * @param version  Name of version
    * @param plugin   Uploaded plugin
    */
  def setPending(owner: String, slug: String, channel: String, version: Version, plugin: PluginFile): PendingVersion = {
    val pending = PendingVersion(owner, slug, channel, Channel.DEFAULT_COLOR, version, plugin)
    pending.cache()
    pending
  }

  /**
    * Returns the pending version for the specified owner, name, channel, and
    * version string.
    *
    * @param owner    Name of owner
    * @param slug     Project slug
    * @param channel  Name of channel
    * @param version  Name of version
    * @return         PendingVersion, if present, None otherwise
    */
  def getPending(owner: String, slug: String, channel: String, version: String): Option[PendingVersion] = {
    Cache.getAs[PendingVersion](owner + '/' + slug + '/' + channel + '/' + version)
  }

  /**
    * Creates a new Version from the specified PluginMetadata.
    *
    * @param project  Project this version belongs to
    * @param meta     PluginMetadata
    * @return         New Version
    */
  def fromMeta(project: Project, meta: PluginMetadata): Version = {
    // TODO: asset parsing
    val depends = for (depend <- meta.getRequiredDependencies) yield depend.getId + ":" + depend.getVersion
    new Version(meta.getVersion, depends.toList, meta.getDescription, "", project.id.getOrElse(-1))
  }

}
