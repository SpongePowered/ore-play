package ore

import javax.inject.{Inject, Singleton}
import models.project.Channel
import ore.Colors._
import org.spongepowered.plugin.meta.version.ComparableVersion
import play.api.{Configuration, Logger}
import util.StringUtils._

/**
  * A helper class for the Ore configuration.
  *
  * @param config Base configuration file
  */
@Singleton
final class OreConfig @Inject()(config: Configuration) {

  // Sub-configs
  lazy val root: Configuration = this.config
  lazy val app: Configuration = this.config.get[Configuration]("application")
  lazy val play: Configuration = this.config.get[Configuration]("play")
  lazy val ore: Configuration = this.config.get[Configuration]("ore")
  lazy val channels: Configuration = this.ore.get[Configuration]("channels")
  lazy val pages: Configuration = this.ore.get[Configuration]("pages")
  lazy val projects: Configuration = this.ore.get[Configuration]("projects")
  lazy val users: Configuration = this.ore.get[Configuration]("users")
  lazy val orgs: Configuration = this.ore.get[Configuration]("orgs")
  lazy val forums: Configuration = this.root.get[Configuration]("discourse")
  lazy val sponge: Configuration = this.root.get[Configuration]("sponge")
  lazy val security: Configuration = this.root.get[Configuration]("security")

  /**
    * The default color used for Channels.
    */
  lazy val defaultChannelColor: Color = Channel.Colors(this.channels.get[Int]("color-default"))

  /**
    * The default name used for Channels.
    */
  lazy val defaultChannelName: String = this.channels.get[String]("name-default")

  /**
    * Returns true if the specified name is a valid Project name.
    *
    * @param name   Name to check
    * @return       True if valid name
    */
  def isValidProjectName(name: String): Boolean = {
    val sanitized = compact(name)
    sanitized.length >= 1 && sanitized.length <= this.projects.get[Int]("max-name-len")
  }

  /**
    * Returns true if the specified string is a valid channel name.
    *
    * @param name   Name to check
    * @return       True if valid channel name
    */
  def isValidChannelName(name: String): Boolean = {
    val c = this.channels
    name.length >= 1 && name.length <= c.get[Int]("max-name-len") && name.matches(c.get[String]("name-regex"))
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
  def getSuggestedNameForVersion(version: String): String
  = Option(new ComparableVersion(version).getFirstString).getOrElse(this.defaultChannelName)

  lazy val debugLevel: Int = this.ore.get[Int]("debug-level")

  /** Returns true if the application is running in debug mode. */
  def isDebug: Boolean = this.ore.get[Boolean]("debug")

  /** Sends a debug message if in debug mode */
  def debug(msg: Any, level: Int = 1): Unit = {
    if (isDebug && (level == this.debugLevel || level == -1))
      Logger.debug(msg.toString)
  }

  /** Asserts that the application is in debug mode. */
  def checkDebug(): Unit = {
    if(!isDebug)
      throw new UnsupportedOperationException("this function is supported in debug mode only")
  }

}
