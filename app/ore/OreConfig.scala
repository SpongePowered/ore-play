package ore

import javax.inject.{Inject, Singleton}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import play.api.{ConfigLoader, Configuration, Logger}

import db.ObjectReference
import models.project.Channel
import util.StringUtils._

import org.spongepowered.plugin.meta.version.ComparableVersion

/**
  * A helper class for the Ore configuration.
  *
  * @param config Base configuration file
  */
@Singleton
final class OreConfig @Inject()(config: Configuration) {

  // Sub-configs
  lazy val root: Configuration = this.config

  object app {
    lazy val raw: Configuration               = root.get[Configuration]("application")
    lazy val baseUrl: String                  = raw.get[String]("baseUrl")
    lazy val dbDefaultTimeout: FiniteDuration = raw.get[FiniteDuration]("db.default-timeout")
    lazy val uploadsDir: String               = raw.get[String]("uploadsDir")

    lazy val trustedUrlHosts: Seq[String] = raw.get[Seq[String]]("trustedUrlHosts")

    object fakeUser {
      lazy val raw: Configuration    = app.raw.get[Configuration]("fakeUser")
      lazy val enabled: Boolean      = raw.get[Boolean]("enabled")
      lazy val id: ObjectReference   = raw.get[ObjectReference]("id")
      lazy val name: Option[String]  = raw.getOptional[String]("name")
      lazy val username: String      = raw.get[String]("username")
      lazy val email: Option[String] = raw.getOptional[String]("email")
    }
  }

  object play {
    lazy val raw: Configuration            = root.get[Configuration]("play")
    lazy val sessionMaxAge: FiniteDuration = raw.get[FiniteDuration]("http.session.maxAge")
  }

  object ore {
    lazy val raw: Configuration = root.get[Configuration]("ore")
    lazy val debug: Boolean     = raw.get[Boolean]("debug")
    lazy val debugLevel: Int    = raw.get[Int]("debug-level")

    object channels {
      lazy val raw: Configuration  = ore.raw.get[Configuration]("channels")
      lazy val maxNameLen: Int     = raw.get[Int]("max-name-len")
      lazy val nameRegex: String   = raw.get[String]("name-regex")
      lazy val colorDefault: Int   = raw.get[Int]("color-default")
      lazy val nameDefault: String = raw.get[String]("name-default")
    }

    object pages {
      lazy val raw: Configuration  = ore.raw.get[Configuration]("pages")
      lazy val homeName: String    = raw.get[String]("home.name")
      lazy val homeMessage: String = raw.get[String]("home.message")
      lazy val minLen: Int         = raw.get[Int]("min-len")
      lazy val maxLen: Int         = raw.get[Int]("max-len")
      lazy val pageMaxLen: Int     = raw.get[Int]("page.max-len")
    }

    object projects {
      lazy val raw: Configuration            = ore.raw.get[Configuration]("projects")
      lazy val maxNameLen: Int               = raw.get[Int]("max-name-len")
      lazy val maxPages: Int                 = raw.get[Int]("max-pages")
      lazy val maxChannels: Int              = raw.get[Int]("max-channels")
      lazy val initLoad: Int                 = raw.get[Int]("init-load")
      lazy val initVersionLoad: Int          = raw.get[Int]("init-version-load")
      lazy val maxDescLen: Int               = raw.get[Int]("max-desc-len")
      lazy val fileValidate: Boolean         = raw.get[Boolean]("file-validate")
      lazy val staleAge: FiniteDuration      = raw.get[FiniteDuration]("staleAge")
      lazy val checkInterval: FiniteDuration = raw.get[FiniteDuration]("check-interval")
      lazy val draftExpire: FiniteDuration   = raw.getOptional[FiniteDuration]("draft-expire").getOrElse(1.day)
    }

    object users {
      lazy val raw: Configuration   = ore.raw.get[Configuration]("users")
      lazy val starsPerPage: Int    = raw.get[Int]("stars-per-page")
      lazy val maxTaglineLen: Int   = raw.get[Int]("max-tagline-len")
      lazy val authorPageSize: Long = raw.get[Long]("author-page-size")
      lazy val projectPageSize: Int = raw.get[Int]("project-page-size")
    }

    object orgs {
      lazy val raw: Configuration       = ore.raw.get[Configuration]("orgs")
      lazy val enabled: Boolean         = raw.get[Boolean]("enabled")
      lazy val dummyEmailDomain: String = raw.get[String]("dummyEmailDomain")
      lazy val createLimit: Int         = raw.get[Int]("createLimit")
    }

    object queue {
      lazy val raw: Configuration            = ore.raw.get[Configuration]("queue")
      lazy val maxReviewTime: FiniteDuration = raw.getOptional[FiniteDuration]("max-review-time").getOrElse(1.day)
    }
  }

  object forums {
    lazy val raw: Configuration        = root.get[Configuration]("discourse")
    lazy val baseUrl: String           = raw.get[String]("baseUrl")
    lazy val categoryDefault: Int      = raw.get[Int]("categoryDefault")
    lazy val categoryDeleted: Int      = raw.get[Int]("categoryDeleted")
    lazy val retryRate: FiniteDuration = raw.get[FiniteDuration]("retryRate")

    object api {
      lazy val raw: Configuration      = forums.raw.get[Configuration]("api")
      lazy val enabled: Boolean        = raw.get[Boolean]("enabled")
      lazy val key: String             = raw.get[String]("key")
      lazy val admin: String           = raw.get[String]("admin")
      lazy val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
    }
  }

  object sponge {
    lazy val raw: Configuration  = root.get[Configuration]("sponge")
    lazy val logo: String        = raw.get[String]("logo")
    lazy val icon: String        = raw.get[String]("icon")
    lazy val service: String     = raw.getOptional[String]("service").getOrElse("unknown")
    lazy val sponsors: Seq[Logo] = raw.get[Seq[Logo]]("sponsors")
  }

  object security {
    lazy val raw: Configuration         = root.get[Configuration]("security")
    lazy val secure: Boolean            = raw.get[Boolean]("secure")
    lazy val requirePgp: Boolean        = raw.get[Boolean]("requirePgp")
    lazy val keyChangeCooldown: Long    = raw.get[Long]("keyChangeCooldown")
    lazy val unsafeDownloadMaxAge: Long = raw.get[Long]("unsafeDownload.maxAge")

    object api {
      lazy val raw: Configuration      = security.raw.get[Configuration]("api")
      lazy val url: String             = raw.get[String]("url")
      lazy val avatarUrl: String       = raw.get[String]("avatarUrl")
      lazy val key: String             = raw.get[String]("key")
      lazy val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
    }

    object sso {
      lazy val raw: Configuration      = security.raw.get[Configuration]("sso")
      lazy val loginUrl: String        = raw.get[String]("loginUrl")
      lazy val signupUrl: String       = raw.get[String]("signupUrl")
      lazy val verifyUrl: String       = raw.get[String]("verifyUrl")
      lazy val secret: String          = raw.get[String]("secret")
      lazy val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
      lazy val apikey: String          = raw.get[String]("apikey")
    }
  }

  object mail {
    lazy val raw: Configuration        = root.get[Configuration]("mail")
    lazy val username: String          = raw.get[String]("username")
    lazy val email: String             = raw.get[String]("email")
    lazy val password: String          = raw.get[String]("password")
    lazy val smtpHost: String          = raw.get[String]("smtp.host")
    lazy val smtpPort: Int             = raw.get[Int]("smtp.port")
    lazy val transportProtocol: String = raw.get[String]("transport.protocol")
    lazy val interval: FiniteDuration  = raw.get[FiniteDuration]("interval")

    lazy val properties: Map[String, String] = raw.get[Map[String, String]]("properties")
  }

  /**
    * The default color used for Channels.
    */
  lazy val defaultChannelColor: Color = Channel.Colors(ore.channels.colorDefault)

  /**
    * The default name used for Channels.
    */
  lazy val defaultChannelName: String = ore.channels.nameDefault

  /**
    * Returns true if the specified name is a valid Project name.
    *
    * @param name   Name to check
    * @return       True if valid name
    */
  def isValidProjectName(name: String): Boolean = {
    val sanitized = compact(name)
    sanitized.length >= 1 && sanitized.length <= ore.projects.maxNameLen
  }

  /**
    * Returns true if the specified string is a valid channel name.
    *
    * @param name   Name to check
    * @return       True if valid channel name
    */
  def isValidChannelName(name: String): Boolean = {
    val c = ore.channels
    name.length >= 1 && name.length <= c.maxNameLen && name.matches(c.nameRegex)
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
  def getSuggestedNameForVersion(version: String): String =
    Option(new ComparableVersion(version).getFirstString).getOrElse(this.defaultChannelName)

  /** Returns true if the application is running in debug mode. */
  def isDebug: Boolean = this.ore.debug

  /** Sends a debug message if in debug mode */
  def debug(msg: Any, level: Int = 1): Unit =
    if (isDebug && (level == ore.debugLevel || level == -1))
      Logger.debug(msg.toString)

  /** Asserts that the application is in debug mode. */
  def checkDebug(): Unit =
    if (!isDebug)
      throw new UnsupportedOperationException("this function is supported in debug mode only")

}

case class Logo(name: String, image: String, link: String)
object Logo {
  implicit val configSeqLoader: ConfigLoader[Seq[Logo]] = ConfigLoader { cfg => path =>
    cfg.getConfigList(path).asScala.map { innerCfg =>
      Logo(
        innerCfg.getString("name"),
        innerCfg.getString("image"),
        innerCfg.getString("link")
      )
    }
  }
}
