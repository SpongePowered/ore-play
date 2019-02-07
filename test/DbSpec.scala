import java.util.concurrent.Executors
import javax.sql.DataSource

import scala.concurrent.ExecutionContext

import play.api.db.Databases
import play.api.db.evolutions.Evolutions

import db.query.DoobieOreProtocol

import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.scalatest.IOChecker
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

trait DbSpec extends FunSuite with Matchers with IOChecker with BeforeAndAfterAll with DoobieOreProtocol {

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
  private lazy val connectExec  = Executors.newFixedThreadPool(32)
  private lazy val transactExec = Executors.newCachedThreadPool
  private lazy val connectEC    = ExecutionContext.fromExecutor(connectExec)
  private lazy val transactEC   = ExecutionContext.fromExecutor(transactExec)

  implicit private val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  lazy val transactor: Transactor.Aux[IO, DataSource] =
    Transactor.fromDataSource[IO](database.dataSource, connectEC, transactEC)

  override def beforeAll(): Unit = Evolutions.applyEvolutions(database)

  override def afterAll(): Unit = {
    database.shutdown()
    connectExec.shutdown()
    transactExec.shutdown()
  }
}
