package util

import javax.inject.Inject

import play.api.libs.json.{JsObject, Json}

import ore.OreConfig

/**
  * Contains status information about the application.
  */
final class StatusZ @Inject()(config: OreConfig) {

  val BuildNum  = "BUILD_NUMBER"
  val GitBranch = "GIT_BRANCH"
  val GitCommit = "GIT_COMMIT"
  val JobName   = "JOB_NAME"
  val BuildTag  = "BUILD_TAG"
  val SpongeEnv = "SPONGE_ENV"
  val Service   = "SERVICE"

  /**
    * Returns a JSON representation of the status.
    *
    * @return JSON status
    */
  def json: JsObject = Json.obj(
    BuildNum  -> env(BuildNum),
    GitBranch -> env(GitBranch),
    GitCommit -> env(GitCommit),
    JobName   -> env(JobName),
    BuildTag  -> env(BuildTag),
    SpongeEnv -> env(SpongeEnv),
    Service   -> config.sponge.service
  )

  private def env(key: String) = sys.env.getOrElse(key, "unknown")

}
