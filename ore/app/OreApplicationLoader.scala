import javax.inject.Provider

import play.api.cache.{DefaultSyncCacheApi, SyncCacheApi}
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.db.slick.{DatabaseConfigProvider, DbName, SlickComponents}
import play.api.db.slick.evolutions.SlickEvolutionsComponents
import play.api.http.EnabledFilters
import play.api.i18n.MessagesApi
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator, Application => PlayApplication}
import play.filters.HttpFiltersComponents

import controllers.apiv2.ApiV2Controller
import controllers.project.{Channels, Pages, Projects, Versions}
import controllers.sugar.Bakery
import controllers._
import db.impl.DbUpdateTask
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.service.OreModelService
import discourse.{OreDiscourseApi, OreDiscourseApiDisabled, OreDiscourseApiEnabled}
import form.OreForms
import mail.{EmailFactory, Mailer, SpongeMailer}
import ore.{OreConfig, OreEnv, StatTracker}
import ore.auth.{AkkaSSOApi, AkkaSpongeAuthApi, SSOApi, SpongeAuthApi}
import ore.db.ModelService
import ore.discourse.AkkaDiscourseApi
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.markdown.{FlexmarkRenderer, MarkdownRenderer}
import ore.models.project.ProjectTask
import ore.models.project.factory.{OreProjectFactory, ProjectFactory}
import ore.models.project.io.ProjectFiles
import ore.models.user.{FakeUser, UserTask}
import ore.rest.{OreRestfulApiV1, OreRestfulServerV1}
import util.StatusZ
import util.uiowrappers._

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import scalaz.zio
import scalaz.zio.{DefaultRuntime, Task, UIO}
import scalaz.zio.interop.catz._
import scalaz.zio.interop.catz.implicits._
import slick.basic.{BasicProfile, DatabaseConfig}

class OreApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): PlayApplication = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    new OreComponents(context).application
  }
}

class OreComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with CaffeineCacheComponents
    with SlickComponents
    with SlickEvolutionsComponents {
  override lazy val router: Router = {
    val prefix = "/"
    wire[_root_.router.Routes]
  }
  lazy val apiV2Routes: _root_.apiv2.Routes = {
    val prefix = "/"
    wire[_root_.apiv2.Routes]
  }

  //override lazy val httpFilters: Seq[EssentialFilter] = enabledFilters.filters
  //lazy val enabledFilters: EnabledFilters             = wire[EnabledFilters] //TODO: This probably won't work

  lazy val syncCacheApi: SyncCacheApi = new DefaultSyncCacheApi(defaultCacheApi)
  lazy val dbConfigProvider: DatabaseConfigProvider = new DatabaseConfigProvider {
    override def get[P <: BasicProfile]: DatabaseConfig[P] = slickApi.dbConfig(DbName("default"))
  }
  implicit lazy val impMessagesApi: MessagesApi = messagesApi
  implicit lazy val impActorSystem: ActorSystem = actorSystem

  implicit lazy val runtime: DefaultRuntime = new DefaultRuntime {}

  type ParUIO[A]  = zio.interop.ParIO[Any, Nothing, A]
  type ParTask[A] = zio.interop.ParIO[Any, Throwable, A]

  implicit lazy val config: OreConfig                  = wire[OreConfig]
  implicit lazy val env: OreEnv                        = wire[OreEnv]
  implicit lazy val markdownRenderer: MarkdownRenderer = wire[FlexmarkRenderer]
  implicit lazy val projectFiles: ProjectFiles         = wire[ProjectFiles]

  lazy val oreRestfulAPIV1: OreRestfulApiV1                          = wire[OreRestfulServerV1]
  implicit lazy val projectFactory: ProjectFactory                   = wire[OreProjectFactory]
  implicit lazy val modelService: ModelService[UIO]                  = new UIOModelService(wire[OreModelService])
  lazy val emailFactory: EmailFactory                                = wire[EmailFactory]
  lazy val mailer: Mailer                                            = wire[SpongeMailer]
  lazy val projectTask: ProjectTask                                  = wire[ProjectTask]
  lazy val userTask: UserTask                                        = wire[UserTask]
  lazy val dbUpdateTask: DbUpdateTask                                = wire[DbUpdateTask]
  implicit lazy val oreControllerComponents: OreControllerComponents = wire[DefaultOreControllerComponents]
  lazy val taskOreControllerEffects: OreControllerEffects[Task]      = wire[TaskOreControllerEffects]
  lazy val uioOreControllerEffects: OreControllerEffects[UIO]        = wire[UIOOreControllerEffects]

  lazy val statTrackerTask: StatTracker[Task] = wire[StatTracker.StatTrackerInstant[Task, ParTask]]
  lazy val statTracker: StatTracker[UIO]      = wire[UIOStatTracker]
  implicit lazy val spongeAuthApiTask: SpongeAuthApi[Task] = {
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
  implicit lazy val spongeAuthApi: SpongeAuthApi[UIO] = wire[UIOSpongeAuthApi]
  lazy val ssoApiTask: SSOApi[Task] = {
    val sso = config.security.sso
    runtime.unsafeRun(AkkaSSOApi[Task](sso.loginUrl, sso.signupUrl, sso.verifyUrl, sso.secret, sso.timeout, sso.reset))
  }
  lazy val ssoApi: SSOApi[UIO] = wire[UIOSSOApi]
  lazy val oreDiscourseApiTask: OreDiscourseApi[Task] = {
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
        actorSystem.scheduler,
        forums.baseUrl,
        api.admin
      )

      forumsApi.start()

      forumsApi
    } else {
      new OreDiscourseApiDisabled[Task]
    }
  }
  implicit lazy val oreDiscourseApi: OreDiscourseApi[UIO] = wire[UIOOreDiscourseApi]
  implicit lazy val userBaseTask: UserBase[Task]          = wire[UserBase.UserBaseF[Task]]
  implicit lazy val userBaseUIO: UserBase[UIO]            = wire[UserBase.UserBaseF[UIO]]
  lazy val projectBaseTask: ProjectBase[Task]             = wire[ProjectBase.ProjectBaseF[Task, ParTask]]
  implicit lazy val projectBaseUIO: ProjectBase[UIO]      = wire[UIOProjectBase]
  lazy val orgBaseTask: OrganizationBase[Task]            = wire[OrganizationBase.OrganizationBaseF[Task, ParTask]]
  implicit lazy val orgBaseUIO: OrganizationBase[UIO]     = wire[UIOOrganizationBase]

  lazy val bakery: Bakery     = wire[Bakery]
  lazy val forms: OreForms    = wire[OreForms]
  lazy val statusZ: StatusZ   = wire[StatusZ]
  lazy val fakeUser: FakeUser = wire[FakeUser]

  lazy val applicationController: Application                   = wire[Application]
  lazy val apiV1Controller: ApiV1Controller                     = wire[ApiV1Controller]
  lazy val apiV2Controller: ApiV2Controller                     = wire[ApiV2Controller]
  lazy val versions: Versions                                   = wire[Versions]
  lazy val users: Users                                         = wire[Users]
  lazy val projects: Projects                                   = wire[Projects]
  lazy val pages: Pages                                         = wire[Pages]
  lazy val organizations: Organizations                         = wire[Organizations]
  lazy val channels: Channels                                   = wire[Channels]
  lazy val reviews: Reviews                                     = wire[Reviews]
  lazy val applicationControllerProvider: Provider[Application] = () => applicationController
  lazy val apiV1ControllerProvider: Provider[ApiV1Controller]   = () => apiV1Controller
  lazy val apiV2ControllerProvider: Provider[ApiV2Controller]   = () => apiV2Controller
  lazy val versionsProvider: Provider[Versions]                 = () => versions
  lazy val usersProvider: Provider[Users]                       = () => users
  lazy val projectsProvider: Provider[Projects]                 = () => projects
  lazy val pagesProvider: Provider[Pages]                       = () => pages
  lazy val organizationsProvider: Provider[Organizations]       = () => organizations
  lazy val channelsProvider: Provider[Channels]                 = () => channels
  lazy val reviewsProvider: Provider[Reviews]                   = () => reviews

  eager(projectTask)
  eager(userTask)
  eager(dbUpdateTask)

  def eager[A](module: A): Unit = {
    identity(module)
    ()
  }
}
