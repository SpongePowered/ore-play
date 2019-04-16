package discourse

import java.nio.file.Path
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import ore.discourse.AkkaDiscourseApi
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.{OreConfig, OreEnv}

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import cats.effect.{Concurrent, ContextShift, IO}

/**
  * [[OreDiscourseApi]] implementation.
  */
@Singleton
class SpongeForums @Inject()(
    env: OreEnv,
    config: OreConfig
)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer)
    extends OreDiscourseApi(
      AkkaDiscourseApi[IO](
        AkkaDiscourseSettings(
          config.forums.api.key,
          config.forums.api.admin,
          config.forums.api.isAvailableReset,
          config.forums.baseUrl
        )
      )({
        implicit val cs: ContextShift[IO] = IO.contextShift(ec)
        Concurrent[IO]
      }, IO.timer(ec), system, mat).unsafeRunSync()
    )(
      IO.contextShift(ec)
    ) {

  private val conf    = this.config.forums
  private val confApi = conf.api

  val isEnabled: Boolean = confApi.enabled

  override val admin: String   = this.confApi.admin
  override val baseUrl: String = this.config.app.baseUrl

  override val categoryDefault: Int                 = this.conf.categoryDefault
  override val categoryDeleted: Int                 = this.conf.categoryDeleted
  override val topicTemplatePath: Path              = this.env.conf.resolve("discourse/project_topic.md")
  override val versionReleasePostTemplatePath: Path = this.env.conf.resolve("discourse/version_post.md")
  override val scheduler: Scheduler                 = this.system.scheduler
  override val retryRate: FiniteDuration            = this.conf.retryRate

}
