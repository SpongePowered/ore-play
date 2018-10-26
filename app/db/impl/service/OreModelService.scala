package db.impl.service

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.db.slick.DatabaseConfigProvider

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
  lazy val DB                                = db.get[JdbcProfile]
  override lazy val DefaultTimeout: Duration = this.config.app.get[Int]("db.default-timeout").seconds

  override def runDBIO[R](action: driver.api.DBIO[R]): Future[R] = DB.db.run(action)

  override def start(): Unit = {
    val time = System.currentTimeMillis()

    Logger.info(
      s"""|Database initialized:
          |Initialization time: ${System.currentTimeMillis() - time}ms
          |Default timeout: ${DefaultTimeout.toString}""".stripMargin
    )
  }

}
