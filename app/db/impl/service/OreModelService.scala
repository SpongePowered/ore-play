package db.impl.service

import java.util.concurrent.Executors
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle

import db.impl.OrePostgresDriver
import ore.{OreConfig, OreEnv}

import cats.effect.{ContextShift, IO}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Strategy
import slick.jdbc.{JdbcDataSource, JdbcProfile}

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
    db: DatabaseConfigProvider,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends OreDBOs(OrePostgresDriver, env, config) {

  val Logger = play.api.Logger("Database")

  // Implement ModelService
  lazy val DB                                = db.get[JdbcProfile]
  override lazy val DefaultTimeout: Duration = this.config.app.dbDefaultTimeout

  implicit lazy val xa: Transactor.Aux[IO, JdbcDataSource] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val connectExec  = Executors.newFixedThreadPool(32)
    val transactExec = Executors.newCachedThreadPool
    val connectEC    = ExecutionContext.fromExecutor(connectExec)
    val transactEC   = ExecutionContext.fromExecutor(transactExec)

    //We stop them separately so one having problems stopping doesn't hinder the other one
    lifecycle.addStopHook { () =>
      Future {
        connectExec.shutdown()
      }
    }

    lifecycle.addStopHook { () =>
      Future {
        transactExec.shutdown()
      }
    }

    Transactor[IO, JdbcDataSource](
      DB.db.source,
      source => cs.evalOn(connectEC)(IO(source.createConnection())),
      KleisliInterpreter[IO](transactEC).ConnectionInterpreter,
      Strategy.default
    )
  }

  override def runDBIO[R](action: driver.api.DBIO[R]): Future[R] = DB.db.run(action)

  override def runDbCon[R](program: ConnectionIO[R]): Future[R] = program.transact(xa).unsafeToFuture()

  override def start(): Unit = {
    val time = System.currentTimeMillis()

    Logger.info(
      s"""|Database initialized:
          |Initialization time: ${System.currentTimeMillis() - time}ms
          |Default timeout: ${DefaultTimeout.toString}""".stripMargin
    )
  }
}
