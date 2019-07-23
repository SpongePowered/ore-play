package db.impl.service

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle

import ore.db.impl.OrePostgresDriver.api._
import ore.db.{Model, ModelCompanion, ModelQuery, ModelService}
import util.TaskUtils

import cats.effect.{Clock, ContextShift, Resource, Sync}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Strategy
import slick.jdbc.{JdbcDataSource, JdbcProfile}
import zio.interop.catz._
import zio.{Task, ZIO}

/**
  * The Ore ModelService implementation. Contains registration of Ore-specific
  * types and Models.
  *
  * @param db DatabaseConfig
  */
@Singleton
class OreModelService @Inject()(
    db: DatabaseConfigProvider,
    lifecycle: ApplicationLifecycle
)(implicit runtime: zio.Runtime[Any])
    extends ModelService[OreModelService.F] {
  import OreModelService.F

  // Implement ModelService
  lazy val DB = db.get[JdbcProfile]

  implicit val clock: Clock[F] = Clock.create[F]

  implicit val xa: Transactor.Aux[F, JdbcDataSource] = {
    val cs = ContextShift[Task]

    TaskUtils.applicationResource(
      runtime,
      for {
        connectEC  <- ExecutionContexts.fixedThreadPool[F](32)
        transactEC <- ExecutionContexts.cachedThreadPool[F]
      } yield Transactor[F, JdbcDataSource](
        DB.db.source,
        source => {
          val acquire                = cs.evalOn(connectEC)(F.delay(source.createConnection()))
          def release(c: Connection) = cs.evalOn(transactEC)(F.delay(c.close()))
          Resource.make(acquire)(release)
        },
        KleisliInterpreter[F](transactEC).ConnectionInterpreter,
        Strategy.default
      )
    )(lifecycle)
  }

  override def runDBIO[R](action: DBIO[R]): F[R] = ZIO.fromFuture(_ => DB.db.run(action))

  override def runDbCon[R](program: ConnectionIO[R]): F[R] = program.transact(xa)

  override def insertRaw[M](companion: ModelCompanion[M])(model: M): F[Model[M]] =
    companion.insert[F](model).flatMap(runDBIO)

  override def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): F[Seq[Model[M]]] =
    query.companion.bulkInsert[F](models).flatMap(runDBIO)

  override def updateRaw[M](companion: ModelCompanion[M])(model: Model[M])(update: M => M): F[Model[M]] =
    companion.update[F](model)(update).flatMap(runDBIO)

  override def delete[M](model: Model[M])(implicit query: ModelQuery[M]): F[Int] =
    runDBIO(query.companion.delete(model))

  override def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): F[Int] =
    runDBIO(model.deleteWhere(filter))
}
object OreModelService {
  type F[A] = Task[A]
  val F: Sync[F] = Sync[F]
}
