package ore

import javax.inject.{Inject, Singleton}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import play.api.{ConfigLoader, Configuration}

import models.project.Channel
import models.user.User
import ore.db.DbRef
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
  val root: Configuration = this.config

  object app extends ConfigCategory {
    val raw: Configuration = root.get[Configuration]("application")
    val baseUrl: String    = raw.get[String]("baseUrl")
    val uploadsDir: String = raw.get[String]("uploadsDir")

    val trustedUrlHosts: Seq[String] = raw.get[Seq[String]]("trustedUrlHosts")

    object fakeUser extends ConfigCategory {
      val raw: Configuration    = app.raw.get[Configuration]("fakeUser")
      val enabled: Boolean      = raw.get[Boolean]("enabled")
      val id: DbRef[User]       = raw.get[DbRef[User]]("id")
      val name: Option[String]  = raw.getOptional[String]("name")
      val username: String      = raw.get[String]("username")
      val email: Option[String] = raw.getOptional[String]("email")
    }
  }

  object play extends ConfigCategory {
    val raw: Configuration            = root.get[Configuration]("play")
    val sessionMaxAge: FiniteDuration = raw.get[FiniteDuration]("http.session.maxAge")
  }

  object ore extends ConfigCategory {
    val raw: Configuration = root.get[Configuration]("ore")
    val debug: Boolean     = raw.get[Boolean]("debug")
    val debugLevel: Int    = raw.get[Int]("debug-level")

    object homepage extends ConfigCategory {
      val raw: Configuration             = ore.raw.get[Configuration]("homepage")
      val updateInterval: FiniteDuration = raw.get[FiniteDuration]("update-interval")
    }

    object channels extends ConfigCategory {
      val raw: Configuration  = ore.raw.get[Configuration]("channels")
      val maxNameLen: Int     = raw.get[Int]("max-name-len")
      val nameRegex: String   = raw.get[String]("name-regex")
      val colorDefault: Int   = raw.get[Int]("color-default")
      val nameDefault: String = raw.get[String]("name-default")
    }

    object pages extends ConfigCategory {
      val raw: Configuration  = ore.raw.get[Configuration]("pages")
      val homeName: String    = raw.get[String]("home.name")
      val homeMessage: String = raw.get[String]("home.message")
      val minLen: Int         = raw.get[Int]("min-len")
      val maxLen: Int         = raw.get[Int]("max-len")
      val pageMaxLen: Int     = raw.get[Int]("page.max-len")
    }

    object projects extends ConfigCategory {
      val raw: Configuration            = ore.raw.get[Configuration]("projects")
      val maxNameLen: Int               = raw.get[Int]("max-name-len")
      val maxPages: Int                 = raw.get[Int]("max-pages")
      val maxChannels: Int              = raw.get[Int]("max-channels")
      val initLoad: Long                = raw.get[Long]("init-load")
      val initVersionLoad: Int          = raw.get[Int]("init-version-load")
      val maxDescLen: Int               = raw.get[Int]("max-desc-len")
      val fileValidate: Boolean         = raw.get[Boolean]("file-validate")
      val staleAge: FiniteDuration      = raw.get[FiniteDuration]("staleAge")
      val checkInterval: FiniteDuration = raw.get[FiniteDuration]("check-interval")
      val draftExpire: FiniteDuration   = raw.getOptional[FiniteDuration]("draft-expire").getOrElse(1.day)
      val stargazersPageSize: Int       = raw.get[Int]("stargazers-page-size")
    }

    object users extends ConfigCategory {
      val raw: Configuration    = ore.raw.get[Configuration]("users")
      val starsPerPage: Int     = raw.get[Int]("stars-per-page")
      val maxTaglineLen: Int    = raw.get[Int]("max-tagline-len")
      val authorPageSize: Long  = raw.get[Long]("author-page-size")
      val projectPageSize: Long = raw.get[Long]("project-page-size")
    }

    object orgs extends ConfigCategory {
      val raw: Configuration       = ore.raw.get[Configuration]("orgs")
      val enabled: Boolean         = raw.get[Boolean]("enabled")
      val dummyEmailDomain: String = raw.get[String]("dummyEmailDomain")
      val createLimit: Int         = raw.get[Int]("createLimit")
    }

    object queue extends ConfigCategory {
      val raw: Configuration            = ore.raw.get[Configuration]("queue")
      val maxReviewTime: FiniteDuration = raw.getOptional[FiniteDuration]("max-review-time").getOrElse(1.day)
    }

    object api extends ConfigCategory {
      val raw: Configuration = ore.raw.get[Configuration]("api")

      object session extends ConfigCategory {
        val raw: Configuration = api.raw.get[Configuration]("session")

        val publicExpiration: FiniteDuration = raw.get[FiniteDuration]("public-expiration")
        val expiration: FiniteDuration       = raw.get[FiniteDuration]("expiration")

        val checkInterval: FiniteDuration = raw.get[FiniteDuration]("check-interval")
      }
    }
  }

  object forums extends ConfigCategory {
    val raw: Configuration        = root.get[Configuration]("discourse")
    val baseUrl: String           = raw.get[String]("baseUrl")
    val categoryDefault: Int      = raw.get[Int]("categoryDefault")
    val categoryDeleted: Int      = raw.get[Int]("categoryDeleted")
    val retryRate: FiniteDuration = raw.get[FiniteDuration]("retryRate")

    object api extends ConfigCategory {
      val raw: Configuration      = forums.raw.get[Configuration]("api")
      val enabled: Boolean        = raw.get[Boolean]("enabled")
      val key: String             = raw.get[String]("key")
      val admin: String           = raw.get[String]("admin")
      val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
    }
  }

  object sponge extends ConfigCategory {
    val raw: Configuration  = root.get[Configuration]("sponge")
    val logo: String        = raw.get[String]("logo")
    val icon: String        = raw.get[String]("icon")
    val service: String     = raw.getOptional[String]("service").getOrElse("unknown")
    val sponsors: Seq[Logo] = raw.get[Seq[Logo]]("sponsors")
  }

  object security extends ConfigCategory {
    val raw: Configuration         = root.get[Configuration]("security")
    val secure: Boolean            = raw.get[Boolean]("secure")
    val unsafeDownloadMaxAge: Long = raw.get[Long]("unsafeDownload.maxAge")

    object api extends ConfigCategory {
      val raw: Configuration      = security.raw.get[Configuration]("api")
      val url: String             = raw.get[String]("url")
      val avatarUrl: String       = raw.get[String]("avatarUrl")
      val key: String             = raw.get[String]("key")
      val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
    }

    object sso extends ConfigCategory {
      val raw: Configuration      = security.raw.get[Configuration]("sso")
      val loginUrl: String        = raw.get[String]("loginUrl")
      val signupUrl: String       = raw.get[String]("signupUrl")
      val verifyUrl: String       = raw.get[String]("verifyUrl")
      val secret: String          = raw.get[String]("secret")
      val timeout: FiniteDuration = raw.get[FiniteDuration]("timeout")
      val apikey: String          = raw.get[String]("apikey")
    }
  }

  object mail extends ConfigCategory {
    val raw: Configuration        = root.get[Configuration]("mail")
    val username: String          = raw.get[String]("username")
    val email: String             = raw.get[String]("email")
    val password: String          = raw.get[String]("password")
    val smtpHost: String          = raw.get[String]("smtp.host")
    val smtpPort: Int             = raw.get[Int]("smtp.port")
    val transportProtocol: String = raw.get[String]("transport.protocol")
    val interval: FiniteDuration  = raw.get[FiniteDuration]("interval")

    val properties: Map[String, String] = raw.get[Map[String, String]]("properties")
  }

  app.load()
  app.fakeUser.load()
  play.load()
  ore.load()
  ore.homepage.load()
  ore.channels.load()
  ore.pages.load()
  ore.projects.load()
  ore.users.load()
  ore.orgs.load()
  ore.queue.load()
  ore.api.load()
  ore.api.session.load()
  forums.load()
  forums.api.load()
  sponge.load()
  security.load()
  security.api.load()
  security.sso.load()
  mail.load()

  /**
    * The default color used for Channels.
    */
  val defaultChannelColor: Color = Channel.Colors(ore.channels.colorDefault)

  /**
    * The default name used for Channels.
    */
  val defaultChannelName: String = ore.channels.nameDefault

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

  /** Asserts that the application is in debug mode. */
  def checkDebug(): Unit =
    if (!isDebug)
      throw new UnsupportedOperationException("this function is supported in debug mode only") // scalafix:ok

}

trait ConfigCategory {
  def load(): Unit = ()
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
