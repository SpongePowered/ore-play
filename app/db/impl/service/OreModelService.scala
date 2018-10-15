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
) extends OreDBOs(OrePostgresDriver, env, config) {

  val Logger = play.api.Logger("Database")

  // Implement ModelService
  override lazy val registry: ModelRegistry  = new ModelRegistry {}
  override lazy val DB                       = db.get[JdbcProfile]
  override lazy val DefaultTimeout: Duration = this.config.app.get[Int]("db.default-timeout").seconds

  import registry.registerModelBase

  override def start(): Unit = {
    val time = System.currentTimeMillis()

    // Initialize database access objects
    registerModelBase(Users)
    registerModelBase(Projects)
    registerModelBase(Organizations)

    Logger.info(
      s"""|Database initialized:
          |Initialization time: ${System.currentTimeMillis() - time}ms
          |Default timeout: ${DefaultTimeout.toString}
          |Registered DBOs: ${this.registry.modelBases.size}""".stripMargin
    )
  }

}
