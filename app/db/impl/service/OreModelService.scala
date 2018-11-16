package db.impl.service

import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._

import play.api.db.slick.DatabaseConfigProvider

import db.ModelRegistry
import db.impl.OrePostgresDriver
import ore.{OreConfig, OreEnv}

import slick.jdbc.JdbcProfile

/**
  * The Ore ModelService implementation. Contains registration of Ore-specific
  * types and Models.
  *
  * @param db DatabaseConfig
  */
@Singleton
class OreModelService @Inject()(
    env: OreEnv,
    config: OreConfig,
    db: DatabaseConfigProvider
) extends OreModelConfig(OrePostgresDriver, env, config) {

  val Logger = play.api.Logger("Database")

  // Implement ModelService
  override lazy val registry: ModelRegistry  = new ModelRegistry {}
  override lazy val DB                       = db.get[JdbcProfile]
  override lazy val DefaultTimeout: Duration = this.config.app.dbDefaultTimeout

  import registry.{registerModelBase, registerSchema}

  override def start(): Unit = {
    val time = System.currentTimeMillis()

    // Initialize database access objects
    registerModelBase(Users)
    registerModelBase(Projects)
    registerModelBase(Organizations)

    // Register model schemas
    registerSchema(UserSchema)
    registerSchema(SessionSchema)
    registerSchema(SignOnSchema)
    registerSchema(ProjectRolesSchema)
    registerSchema(ProjectSchema)
    registerSchema(ProjectSettingsSchema)
    registerSchema(ProjectLogSchema)
    registerSchema(ProjectLogEntrySchema)
    registerSchema(FlagSchema)
    registerSchema(ViewSchema)
    registerSchema(ReviewSchema)
    registerSchema(VersionSchema)
    registerSchema(VersionTagSchema)
    registerSchema(DownloadWarningSchema)
    registerSchema(UnsafeDownloadSchema)
    registerSchema(DownloadSchema)
    registerSchema(ChannelSchema)
    registerSchema(PageSchema)
    registerSchema(NotificationSchema)
    registerSchema(OrganizationSchema)
    registerSchema(OrganizationRoleSchema)
    registerSchema(ProjectApiKeySchema)
    registerSchema(UserActionLogSchema)
    registerSchema(ProjectVisibilityChangeSchema)
    registerSchema(VersionVisibilityChangeSchema)
    registerSchema(DbRoleSchema)

    Logger.info(
      "Database initialized:\n" +
        s"Initialization time: ${System.currentTimeMillis() - time}ms\n" +
        s"Default timeout: ${DefaultTimeout.toString}\n" +
        s"Registered DBOs: ${this.registry.modelBases.size}\n" +
        s"Registered Schemas: ${this.registry.modelSchemas.size}"
    )
  }

}
