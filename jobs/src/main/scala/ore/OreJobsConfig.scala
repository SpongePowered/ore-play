package ore

import scala.concurrent.duration.FiniteDuration

import pureconfig._
import pureconfig.generic.auto._

trait Config {
  val config: OreJobsConfig
}

case class OreJobsConfig(
    ore: OreJobsConfig.Ore,
    discourse: OreJobsConfig.Discourse,
    jobs: OreJobsConfig.Jobs
)

object OreJobsConfig {
  def load: ConfigReader.Result[OreJobsConfig] = ConfigSource.default.load[OreJobsConfig]

  case class Ore(baseUrl: String, pages: OrePages)
  case class OrePages(home: OrePagesHome)
  case class OrePagesHome(name: String)

  case class Discourse(
      baseUrl: String,
      categoryDefault: Int,
      categoryDeleted: Int,
      api: ForumsApi
  )

  case class ForumsApi(
      enabled: Boolean,
      key: String,
      admin: String,
      breaker: BreakerSettings
  )

  case class BreakerSettings(
      maxFailures: Int,
      reset: FiniteDuration,
      timeout: FiniteDuration
  )

  case class Jobs(
      checkInterval: FiniteDuration,
      timeouts: JobsTimeouts
  )

  case class JobsTimeouts(
      unknownError: FiniteDuration,
      statusError: FiniteDuration,
      notAvailable: FiniteDuration
  )
}
