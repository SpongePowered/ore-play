package models.project

import java.nio.file.Files
import java.sql.Timestamp

import com.google.common.base.Preconditions._
import db.Storage
import models.project.Channel._
import models.project.ChannelColors.ChannelColor
import org.apache.commons.io.FileUtils
import org.spongepowered.plugin.meta.version.ComparableVersion
import org.spongepowered.plugin.meta.version.ComparableVersion.{ListItem, StringItem}
import plugin.ProjectManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Represents a release channel for Project Versions. Each project gets it's
  * own set of channels.
  *
  * TODO: Max channels per-project
  *
  * @param id         Unique identifier
  * @param createdAt  Instant of creation
  * @param name       Name of channel
  * @param colorId    ID of ChannelColor used to represent this Channel
  * @param projectId  ID of project this channel belongs to
  */
case class Channel(id: Option[Int], var createdAt: Option[Timestamp], private var name: String,
                   private var colorId: Int, projectId: Int) extends Ordered[Channel] {

  def this(name: String, color: ChannelColor, projectId: Int) = this(None, None, name, color.id, projectId)

  /**
    * Returns this Channel's name.
    *
    * @return Channel name
    */
  def getName: String = this.name

  /**
    * Sets the name of this channel for.
    *
    * @param context  Project for context
    * @param name     New channel name
    * @return         Future result
    */
  def setName(context: Project, name: String): Future[Int] = {
    checkArgument(context.id.get == this.projectId, "invalid context id", "")
    checkArgument(isValidName(name), "invalid name", "")
    val f = Storage.updateChannelString(this, _.name, name)
    f.onSuccess {
      case i =>
        ProjectManager.renameChannel(context.owner, context.getName, this.name, name)
        this.name = name
    }
    f
  }

  /**
    * Returns the ChannelColor that this Channel is represented by.
    *
    * @return Color channel is represented by
    */
  def getColor: ChannelColor = ChannelColors(this.colorId)

  /**
    * Sets the color of this channel.
    *
    * @param color  Color of channel
    * @return       Future result
    */
  def setColor(color: ChannelColor): Future[Int] = {
    val f = Storage.updateChannelInt(this, _.colorId, color.id)
    f.onSuccess {
      case i => this.colorId = color.id
    }
    f
  }

  /**
    * Returns the Project this Channel belongs to.
    *
    * @return Project the Channel belongs to
    */
  def getProject: Future[Project] = Storage.getProject(this.projectId)

  /**
    * Returns all Versions in this channel.
    *
    * @return All versions
    */
  def getVersions: Future[Seq[Version]] = Storage.getVersions(this.id.get)

  /**
    * Returns the Version in this channel with the specified version string.
    *
    * @param version  Version string
    * @return         Version, if any, None otherwise
    */
  def getVersion(version: String): Future[Option[Version]] = Storage.optVersion(this.id.get, version)

  /**
    * Returns the amount of versions in this Channel.
    *
    * @return Amount of versions
    */
  def versionCount: Int = Storage.now(getVersions) match {
    case Failure(thrown) => throw thrown
    case Success(versions) => versions.size
  }

  def isEmpty: Boolean = versionCount == 0

  /**
    * Deletes the specified Version within this channel.
    *
    * @param version  Version to delete
    * @param context  Project for context
    * @return         Result
    */
  def deleteVersion(version: Version, context: Project): Try[Unit] = Try {
    checkArgument(context.getVersions.size > 1, "only one version", "")
    checkArgument(context.id.get == this.projectId, "invalid context id", "")
    Storage.now(Storage.deleteVersion(version)) match {
      case Failure(thrown) => throw thrown
      case Success(i) =>
        Files.delete(ProjectManager.getUploadPath(context.owner, context.getName, version.versionString, this.name))
    }
  }

  /**
    * Irreversibly deletes this channel and all version associated with it.
    *
    * @param context  Project context
    * @return         Result
    */
  def delete(context: Project): Try[Unit] = Try {
    checkArgument(context.id.get == this.projectId, "invalid context id", "")
    Storage.now(context.getChannels) match {
      case Failure(thrown) => throw thrown
      case Success(channels) =>
        checkArgument(channels.size > 1, "only one channel", "")
        checkArgument(isEmpty || channels.count(c => c.versionCount > 0) > 1, "last non-empty channel", "")
    }
    Storage.now(Storage.deleteChannel(this)) match {
      case Failure(thrown) => throw thrown
      case Success(i) =>
        FileUtils.deleteDirectory(ProjectManager.getProjectDir(context.owner, context.getName).resolve(this.name).toFile)
    }
  }

  def newVersion(version: String, dependencies: List[String], description: String, assets: String): Future[Version] = {
    Storage.createVersion(new Version(version, dependencies, description, assets, this.projectId, this.id.get))
  }

  override def compare(that: Channel): Int = this.name compare that.name

  override def hashCode: Int = this.id.get.hashCode

  override def equals(o: Any): Boolean = {
    o.isInstanceOf[Channel] && o.asInstanceOf[Channel].id.get == this.id.get
  }

}

object Channel {

  /**
    * The maximum amount of Channels permitted in a single Project.
    */
  val MAX_AMOUNT = 5

  /**
    * The maximum name size of a Channel.
    */
  val MAX_NAME_LENGTH = 15

  /**
    * Regular expression for permitted Channel characters.
    */
  val NAME_REGEX = "^[a-zA-Z0-9]+$"

  /**
    * The default color used for Channels.
    */
  val DEFAULT_COLOR: ChannelColor = ChannelColors.DarkGreen

  /**
    * The default name used for Channels.
    */
  val DEFAULT_CHANNEL: String = "Release"

  /**
    * Returns true if the specified string is a valid channel name.
    *
    * @param name   Name to check
    * @return       True if valid channel name
    */
  def isValidName(name: String): Boolean = {
    name.length >= 1 && name.length <= MAX_NAME_LENGTH && name.matches(NAME_REGEX);
  }

  /**
    * Attempts to determine a Channel name from the specified version string.
    * This is attained using a ComparableVersion and finding the first
    * StringItem within the parsed version. (e.g. 1.0.0-alpha) would return
    * "alpha".
    *
    * @param version  Version string to parse
    * @return         Suggested channel name
    */
  def getSuggestedNameForVersion(version: String): String = {
    firstString(new ComparableVersion(version).getItems).getOrElse(DEFAULT_CHANNEL)
  }

  private def firstString(items: ListItem): Option[String] = {
    // Get the first non-number component in the version string
    var str: Option[String] = None
    var i = 0
    while (str.isEmpty && i < items.size()) {
      items.get(i) match {
        case item: StringItem => str = Some(item.getValue)
        case item: ListItem => str = firstString(item)
        case _ => ;
      }
      i += 1
    }
    str
  }

}
