package discourse

import java.nio.file.Path
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import play.api.libs.ws.WSClient

import db.ModelService
import ore.{OreConfig, OreEnv}

import akka.actor.{ActorSystem, Scheduler}
import cats.effect.IO

/**
  * [[OreDiscourseApi]] implementation.
  */
@Singleton
class SpongeForums @Inject()(
    env: OreEnv,
    config: OreConfig,
    actorSystem: ActorSystem,
    val bootstrapService: ModelService,
    val bootstrapConfig: OreConfig,
    override val ws: WSClient
)(implicit ec: ExecutionContext)
    extends OreDiscourseApi()(IO.contextShift(ec), IO.timer(ec)) {

  private val conf = this.config.forums
  private val api  = conf.api

  val isEnabled: Boolean = api.enabled
  isDebugMode = this.config.ore.debug

  override val key: String       = this.api.key
  override val admin: String     = this.api.admin
  override val timeout: Duration = this.api.timeout
  override val url: String       = this.conf.baseUrl
  override val baseUrl: String   = this.config.app.baseUrl

  override val categoryDefault: Int                 = this.conf.categoryDefault
  override val categoryDeleted: Int                 = this.conf.categoryDeleted
  override val topicTemplatePath: Path              = this.env.conf.resolve("discourse/project_topic.md")
  override val versionReleasePostTemplatePath: Path = this.env.conf.resolve("discourse/version_post.md")
  override val scheduler: Scheduler                 = this.actorSystem.scheduler
  override val retryRate: FiniteDuration            = this.conf.retryRate

}
