import javax.sql.DataSource

import play.api.db.Databases
import play.api.db.evolutions.Evolutions

import db.query.DoobieOreProtocol

import cats.effect.IO
import doobie.Transactor
import doobie.scalatest.IOChecker
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

trait DbSpec extends FunSuite with Matchers with IOChecker with BeforeAndAfterAll with DoobieOreProtocol {

  lazy val database = Databases(
    "org.postgresql.Driver",
    sys.env.getOrElse("ORE_TESTDB_JDBC", "jdbc:postgresql://localhost/ore_test"),
    config = Map(
      "username" -> sys.env.getOrElse("DB_USERNAME", "ore"),
      "password" -> sys.env.getOrElse("DB_PASSWORD", "")
    )
  )

  lazy val transactor: Transactor.Aux[IO, DataSource] =
    Transactor.fromDataSource[IO](database.dataSource)

  override def beforeAll(): Unit = Evolutions.applyEvolutions(database)

  override def afterAll(): Unit = database.shutdown()
}
