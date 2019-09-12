package db

import java.util.concurrent.Executors
import javax.sql.DataSource

import scala.concurrent.ExecutionContext
import scala.util.Random

import play.api.db.Databases
import play.api.db.slick.DatabaseConfigProvider

import db.impl.service.OreModelService
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.{DefaultModelCompanion, OrePostgresDriver}
import ore.db.{Model, ModelQuery, ObjId, ObjInstant}

import com.typesafe.config.{Config, ConfigFactory}
import doobie.util.transactor.Transactor
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.scalatestplus.junit.JUnitRunner
import slick.basic.{BasicProfile, DatabaseConfig}
import zio.interop.catz._
import zio.{DefaultRuntime, Task}

@RunWith(classOf[JUnitRunner])
class ModelServiceSpec extends FunSuite with Matchers with BeforeAndAfterAll { self =>

  implicit val runtime: zio.Runtime[Any] = new DefaultRuntime {}

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

  lazy val transactor: Transactor.Aux[Task, DataSource] =
    Transactor.fromDataSource[Task](database.dataSource, connectEC, transactEC)(taskEffectInstance, zioContextShift)

  val service = new OreModelService(
    new DatabaseConfigProvider { provider =>
      private val db = Database.forDataSource(database.dataSource, None)

      override def get[P <: BasicProfile] = new DatabaseConfig[P] {
        override def db: P#Backend#Database = provider.db.asInstanceOf[P#Backend#Database]

        override val profile: P = OrePostgresDriver.asInstanceOf[P]
        override val driver: P  = OrePostgresDriver.asInstanceOf[P]

        override def config: Config = ConfigFactory.empty()

        override def profileName: String = "slick.jdbc.PostgresProfile"

        override def profileIsObject: Boolean = true
      }
    },
    transactor
  )

  class MyObjTable(tag: Tag) extends ModelTable[MyObj](tag, "my_objs") {
    def foo = column[String]("foo")
    def bar = column[Int]("bar")
    def baz = column[Long]("baz")

    def * =
      (id.?, createdAt.?, (foo, bar, baz).<>[MyObj]((MyObj.apply _).tupled, MyObj.unapply)).<>[Model[MyObj]](
        { case (id, time, obj) => Model(ObjId.unsafeFromOption(id), ObjInstant.unsafeFromOption(time), obj) },
        (Model.unapply[MyObj] _)
          .andThen(_.map { case (id, time, obj) => (id.unsafeToOption, time.unsafeToOption, obj) })
      )
  }
  case class MyObj(foo: String, bar: Int, baz: Long)
  object MyObj extends DefaultModelCompanion[MyObj, MyObjTable](TableQuery[MyObjTable]) {
    implicit val query: ModelQuery[MyObj] = ModelQuery.from(this)
  }

  override protected def beforeAll(): Unit = {
    //Slick's generated DDL is stupid and doesn't add the auto increment part
    runtime.unsafeRun(
      service.runDBIO(
        sql"""|create table if not exists "my_objs" (
              |  "id" BIGSERIAL PRIMARY KEY,
              |  "created_at" TIMESTAMP NOT NULL,
              |  "foo" VARCHAR NOT NULL,
              |  "bar" INTEGER NOT NULL,
              |  "baz" BIGINT NOT NULL
              |)""".stripMargin.asUpdate
      )
    )
    ()
  }

  def createTestObj() = MyObj(Random.nextString(Random.nextInt(64)), Random.nextInt(), Random.nextLong())

  test("Insert object should contain the inserted object") {
    val obj = createTestObj()

    runtime.unsafeRun(service.insert(obj).map(_.obj)) should equal(obj)
  }

  test("Inserted objects should have a correct id") {
    val obj = createTestObj()

    val program = for {
      inserted <- service.insert(obj)
      gotten   <- service.runDBIO(TableQuery[MyObjTable].filter(_.id === inserted.id.value).result)
    } yield gotten.map(_.obj)

    runtime.unsafeRun(program) should equal(Seq(obj))
  }

  test("Update should return the updated object") {
    val obj    = createTestObj()
    val newFoo = Random.nextString(Random.nextInt(64))

    val program = for {
      inserted <- service.insert(obj)
      updated  <- service.update(inserted)(_.copy(foo = newFoo))
    } yield updated.obj

    runtime.unsafeRun(program) should equal(obj.copy(foo = newFoo))
  }

  test("BulkInsert should insert all elements") {
    val objs = Seq.fill(Random.nextInt(16))(createTestObj())

    runtime.unsafeRun(service.bulkInsert(objs).map(_.map(_.obj).toSet)) should equal(objs.toSet)
  }

  test("Delete should remove the deleted element") {
    val obj = createTestObj()

    val program = for {
      inserted     <- service.insert(obj)
      existsBefore <- service.runDBIO(TableQuery[MyObjTable].filter(_.id === inserted.id.value).exists.result)
      _            <- service.delete(inserted)
      existsAfter  <- service.runDBIO(TableQuery[MyObjTable].filter(_.id === inserted.id.value).exists.result)
    } yield existsBefore && !existsAfter

    assert(runtime.unsafeRun(program))
  }

  override def afterAll(): Unit = {
    runtime.unsafeRun(service.runDBIO(TableQuery[MyObjTable].schema.dropIfExists))

    database.shutdown()
    connectExec.shutdown()
    transactExec.shutdown()
  }

}
