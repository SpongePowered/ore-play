package discourse

import java.nio.file.Path

import akka.actor.{ActorSystem, Scheduler}
import javax.inject.{Inject, Singleton}

import ore.{OreConfig, OreEnv}
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import db.ModelService

/**
  * [[OreDiscourseApi]] implementation.
  */
@Singleton
class SpongeForums @Inject()(env: OreEnv,
                             config: OreConfig,
                             actorSystem: ActorSystem,
                             val bootstrapService: ModelService,
                             val bootstrapConfig: OreConfig,
                             override val ws: WSClient) extends OreDiscourseApi {

  private val conf = this.config.forums

  isEnabled = this.conf.get[Boolean]("api.enabled")
  isDebugMode = this.config.ore.get[Boolean]("debug")

  override val key: String = this.conf.get[String]("api.key")
  override val admin: String = this.conf.get[String]("api.admin")
  override val timeout: Duration = this.conf.get[FiniteDuration]("api.timeout")
  override val url: String = this.conf.get[String]("baseUrl")
  override val baseUrl: String = this.config.app.get[String]("baseUrl")

  override val categoryDefault: Int = this.conf.get[Int]("categoryDefault")
  override val categoryDeleted: Int = this.conf.get[Int]("categoryDeleted")
  override val topicTemplatePath: Path = this.env.conf.resolve("discourse/project_topic.md")
  override val versionReleasePostTemplatePath: Path = this.env.conf.resolve("discourse/version_post.md")
  override val scheduler: Scheduler = this.actorSystem.scheduler
  override val bootstrapExecutionContext: ExecutionContext = this.actorSystem.dispatcher
  override val retryRate: FiniteDuration = this.conf.get[FiniteDuration]("retryRate")

}
