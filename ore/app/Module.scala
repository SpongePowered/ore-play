import javax.inject.Singleton

import scala.concurrent.ExecutionContext

import controllers.sugar.Bakery
import controllers.{DefaultOreControllerComponents, OreControllerComponents}
import db.impl.DbUpdateTask
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.service.OreModelService
import discourse.{OreDiscourseApi, OreDiscourseApiDisabled, OreDiscourseApiEnabled}
import mail.{Mailer, SpongeMailer}
import ore.auth.{AkkaSSOApi, AkkaSpongeAuthApi, SSOApi, SpongeAuthApi}
import ore.db.ModelService
import ore.discourse.AkkaDiscourseApi
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.markdown.{FlexmarkRenderer, MarkdownRenderer}
import ore.models.project.ProjectTask
import ore.models.project.factory.{OreProjectFactory, ProjectFactory}
import ore.models.project.io.ProjectFiles
import ore.models.user.UserTask
import ore.rest.{OreRestfulApiV1, OreRestfulServerV1}
import ore.{OreConfig, OreEnv, StatTracker}

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.{AbstractModule, Provides, TypeLiteral}

/** The Ore Module */
class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MarkdownRenderer]).to(classOf[FlexmarkRenderer])
    bind(classOf[OreRestfulApiV1]).to(classOf[OreRestfulServerV1])
    bind(classOf[ProjectFactory]).to(classOf[OreProjectFactory])
    bind(new TypeLiteral[ModelService[IO]] {}).to(classOf[OreModelService])
    bind(classOf[Mailer]).to(classOf[SpongeMailer])
    bind(classOf[ProjectTask]).asEagerSingleton()
    bind(classOf[UserTask]).asEagerSingleton()
    bind(classOf[DbUpdateTask]).asEagerSingleton()
    bind(new TypeLiteral[OreControllerComponents[IO]] {}).to(classOf[DefaultOreControllerComponents])
    ()
  }

  @Provides
  @Singleton
  def provideStatTracker(
      bakery: Bakery,
      ec: ExecutionContext
  )(implicit service: ModelService[IO], users: UserBase[IO]): StatTracker[IO] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    new StatTracker.StatTrackerInstant(bakery)
  }

  @Provides
  @Singleton
  def provideAuthApi(
      config: OreConfig,
      ec: ExecutionContext
  )(implicit system: ActorSystem, mat: Materializer): SpongeAuthApi[IO] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    val api                           = config.security.api
    AkkaSpongeAuthApi[IO](
      AkkaSpongeAuthApi.AkkaSpongeAuthSettings(
        api.key,
        api.url,
        api.breaker.maxFailures,
        api.breaker.reset,
        api.breaker.timeout
      )
    ).unsafeRunSync()
  }

  @Provides
  @Singleton
  def provideSSOApi(
      config: OreConfig,
      ec: ExecutionContext
  )(implicit system: ActorSystem, mat: Materializer): SSOApi[IO] = {
    val sso                           = config.security.sso
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    implicit val timer: Timer[IO]     = IO.timer(ec)

    AkkaSSOApi[IO](sso.loginUrl, sso.signupUrl, sso.verifyUrl, sso.secret, sso.timeout, sso.reset).unsafeRunSync()
  }

  @Provides
  @Singleton
  def provideOreDiscourseApi(env: OreEnv)(
      implicit service: ModelService[IO],
      config: OreConfig,
      ec: ExecutionContext,
      system: ActorSystem,
      mat: Materializer
  ): OreDiscourseApi[IO] = {
    val forums = config.forums
    if (forums.api.enabled) {
      val api = forums.api

      implicit val cs: ContextShift[IO] = IO.contextShift(ec)

      val discourseApi = AkkaDiscourseApi[IO](
        AkkaDiscourseSettings(
          api.key,
          api.admin,
          forums.baseUrl,
          api.breaker.maxFailures,
          api.breaker.reset,
          api.breaker.timeout,
        )
      ).unsafeRunSync()

      val forumsApi = new OreDiscourseApiEnabled(
        discourseApi,
        forums.categoryDefault,
        forums.categoryDeleted,
        env.conf.resolve("discourse/project_topic.md"),
        env.conf.resolve("discourse/version_post.md"),
        forums.retryRate,
        system.scheduler,
        forums.baseUrl,
        api.admin
      )

      forumsApi.start()

      forumsApi
    } else {
      new OreDiscourseApiDisabled
    }
  }

  @Provides
  @Singleton
  def provideUserBase(implicit service: ModelService[IO], authApi: SpongeAuthApi[IO], config: OreConfig): UserBase[IO] =
    new UserBase.UserBaseF()

  @Provides
  @Singleton
  def provideProjectBase(
      ec: ExecutionContext
  )(
      implicit service: ModelService[IO],
      fileManager: ProjectFiles,
      config: OreConfig,
      forums: OreDiscourseApi[IO]
  ): ProjectBase[IO] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    new ProjectBase.ProjectBaseF()
  }

  @Provides
  @Singleton
  def provideOrganizationBase(
      ec: ExecutionContext
  )(
      implicit service: ModelService[IO],
      config: OreConfig,
      authApi: SpongeAuthApi[IO],
      users: UserBase[IO]
  ): OrganizationBase[IO] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    new OrganizationBase.OrganizationBaseF()
  }

}
