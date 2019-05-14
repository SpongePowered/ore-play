package db.impl.service

import java.util.concurrent.Executors
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle

import ore.db.{Model, ModelCompanion, ModelQuery, ModelService}
import ore.db.impl.OrePostgresDriver.api._
import ore.{OreConfig, OreEnv}

import cats.effect.{Clock, ContextShift, IO}
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
    extends ModelService[IO] {

  // Implement ModelService
  lazy val DB = db.get[JdbcProfile]

  implicit val clock: Clock[IO] = Clock.create[IO]

  implicit val xa: Transactor.Aux[IO, JdbcDataSource] = {
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

  override def runDBIO[R](action: DBIO[R]): IO[R] = IO.fromFuture(IO(DB.db.run(action)))

  override def runDbCon[R](program: ConnectionIO[R]): IO[R] = program.transact(xa)

  override def insertRaw[M](companion: ModelCompanion[M])(model: M): IO[Model[M]] =
    companion.insert[IO](model).flatMap(runDBIO)

  override def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): IO[Seq[Model[M]]] =
    query.companion.bulkInsert[IO](models).flatMap(runDBIO)

  override def update[M](model: Model[M])(update: M => M)(implicit query: ModelQuery[M]): IO[Model[M]] =
    query.companion.update[IO](model)(update).flatMap(runDBIO)

  override def delete[M](model: Model[M])(implicit query: ModelQuery[M]): IO[Int] =
    runDBIO(query.companion.delete(model))

  override def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): IO[Int] =
    runDBIO(model.deleteWhere(filter))
}
