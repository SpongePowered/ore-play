package db

import java.util.concurrent.Executors
import javax.sql.DataSource

import scala.concurrent.ExecutionContext

import play.api.db.Databases
import play.api.db.evolutions.Evolutions

import ore.db.impl.query.DoobieOreProtocol

import cats.effect.{Blocker, Effect}
import doobie.Transactor
import doobie.scalatest.Checker
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.matchers.should.Matchers
import zio.interop.catz.{taskEffectInstance, zioContextShift}
import zio.{Runtime, Task}

trait BaseDbSpec extends Matchers with Checker[Task] with BeforeAndAfterAll with DoobieOreProtocol { this: Suite =>

  implicit val runtime: zio.Runtime[Any] = Runtime.default

  implicit override def M: Effect[Task] = taskEffectInstance

  lazy val database = Databases(
    "org.postgresql.Driver",
    sys.env.getOrElse(
      "ORE_TESTDB_JDBC",
      "jdbc:postgresql://localhost" + sys.env.get("PGPORT").map(":" + _).getOrElse("") + "/" + sys.env
        .getOrElse("DB_DATABASE", "ore_test")
    ),
    config = Map(
      "username" -> sys.env.getOrElse("DB_USERNAME", "ore"),
      "password" -> sys.env.getOrElse("DB_PASSWORD", "")
    )
  )
  private lazy val connectEC  = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(32))
  private lazy val transactEC = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
  lazy val transactor: Transactor.Aux[Task, DataSource] =
    Transactor.fromDataSource[Task](database.dataSource, connectEC, Blocker.liftExecutionContext(transactEC))(
      taskEffectInstance,
      zioContextShift
    )

  override def beforeAll(): Unit = Evolutions.applyEvolutions(database)

  override def afterAll(): Unit = {
    database.shutdown()
    connectEC.shutdown()
    transactEC.shutdown()
  }
}
