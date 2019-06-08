import javax.inject.Singleton

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
import util.uiowrappers.{UIOModelService, UIOSSOApi}

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{AbstractModule, Provides, TypeLiteral}
import scalaz.zio
import scalaz.zio.blocking.Blocking
import scalaz.zio.{Task, UIO}
import scalaz.zio.interop.catz._
import scalaz.zio.interop.catz.implicits._

/** The Ore Module */
class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MarkdownRenderer]).to(classOf[FlexmarkRenderer])
    bind(classOf[OreRestfulApiV1]).to(classOf[OreRestfulServerV1])
    bind(classOf[ProjectFactory]).to(classOf[OreProjectFactory])
    bind(new TypeLiteral[ModelService[Task]] {}).to(classOf[OreModelService])
    bind(classOf[Mailer]).to(classOf[SpongeMailer])
    bind(classOf[ProjectTask]).asEagerSingleton()
    bind(classOf[UserTask]).asEagerSingleton()
    bind(classOf[DbUpdateTask]).asEagerSingleton()
    bind(new TypeLiteral[OreControllerComponents] {}).to(classOf[DefaultOreControllerComponents])
    ()
  }

  @Provides
  @Singleton
  def provideStatTracker(
      bakery: Bakery,
  )(implicit service: ModelService[Task], users: UserBase[Task], runtime: zio.Runtime[Any]): StatTracker[Task] =
    new StatTracker.StatTrackerInstant(bakery)

  @Provides
  @Singleton
  def provideAuthApi(
      config: OreConfig
  )(implicit system: ActorSystem, mat: Materializer, runtime: zio.Runtime[Any]): SpongeAuthApi[Task] = {
    val api = config.security.api
    runtime.unsafeRun(
      AkkaSpongeAuthApi[Task](
        AkkaSpongeAuthApi.AkkaSpongeAuthSettings(
          api.key,
          api.url,
          api.breaker.maxFailures,
          api.breaker.reset,
          api.breaker.timeout
        )
      )
    )
  }

  @Provides
  @Singleton
  def provideSSOApi(
      config: OreConfig,
  )(implicit system: ActorSystem, mat: Materializer, runtime: zio.Runtime[Any]): SSOApi[Task] = {
    val sso = config.security.sso

    runtime.unsafeRun(AkkaSSOApi[Task](sso.loginUrl, sso.signupUrl, sso.verifyUrl, sso.secret, sso.timeout, sso.reset))
  }

  @Provides
  @Singleton
  def provideOreDiscourseApi(env: OreEnv)(
      implicit service: ModelService[Task],
      config: OreConfig,
      system: ActorSystem,
      mat: Materializer,
      runtime: zio.Runtime[Any]
  ): OreDiscourseApi[Task] = {
    val forums = config.forums
    if (forums.api.enabled) {
      val api = forums.api

      val discourseApi = runtime.unsafeRun(
        AkkaDiscourseApi[Task](
          AkkaDiscourseSettings(
            api.key,
            api.admin,
            forums.baseUrl,
            api.breaker.maxFailures,
            api.breaker.reset,
            api.breaker.timeout,
          )
        )
      )

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

      //forumsApi.start()
      ???

      forumsApi
    } else {
      new OreDiscourseApiDisabled[Task]
    }
  }

  @Provides
  @Singleton
  def provideModelServiceUIO(service: ModelService[Task]) = new UIOModelService(service)

  @Provides
  @Singleton
  def provideSSOApiUIO(sso: SSOApi[Task]) = new UIOSSOApi(sso)

  @Provides
  @Singleton
  def provideUserBaseTask(
      implicit service: ModelService[Task],
      authApi: SpongeAuthApi[Task],
      config: OreConfig
  ): UserBase[Task] =
    new UserBase.UserBaseF()

  @Provides
  @Singleton
  def provideUserBaseUIO(
      implicit service: ModelService[UIO],
      authApi: SpongeAuthApi[UIO],
      config: OreConfig
  ): UserBase[UIO] =
    new UserBase.UserBaseF()

  @Provides
  @Singleton
  def provideProjectBaseTask(
      implicit service: ModelService[Task],
      fileManager: ProjectFiles,
      config: OreConfig,
      forums: OreDiscourseApi[Task],
      runtime: zio.Runtime[Blocking]
  ): ProjectBase[Task] =
    new ProjectBase.ProjectBaseF()

  @Provides
  @Singleton
  def provideOrganizationBaseTask(
      implicit service: ModelService[Task],
      config: OreConfig,
      authApi: SpongeAuthApi[Task],
      users: UserBase[Task]
  ): OrganizationBase[Task] =
    new OrganizationBase.OrganizationBaseF()

}
