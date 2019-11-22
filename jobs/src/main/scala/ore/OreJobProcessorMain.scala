package ore

import java.nio.file.{Path, Paths}
import java.sql.Connection

import scala.util.chaining._

import ore.db.ModelService
import ore.db.impl.OrePostgresDriver
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.service.OreModelService
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.discourse.{AkkaDiscourseApi, Discourse, OreDiscourseApi, OreDiscourseApiEnabled}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.{Blocker, Resource}
import cats.tagless.syntax.all._
import cats.~>
import com.typesafe.scalalogging
import doobie.free.KleisliInterpreter
import doobie.util.ExecutionContexts
import doobie.util.transactor.{Strategy, Transactor}
import slick.jdbc.JdbcDataSource
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._
import zio.random.Random
import zio.system.System

object OreJobProcessorMain extends zio.ManagedApp {
  private val Logger = scalalogging.Logger("OreJobsMain")

  type SlickDb = OrePostgresDriver.backend.DatabaseDef

  val resources: Path = Paths.get(this.getClass.getClassLoader.getResource("application.conf").toURI).getParent

  override def run(args: List[String]): ZManaged[Environment, Nothing, Int] = {
    (for {
      db         <- slickDb.flatMapError(logErrorManaged("Failed to connect to db"))
      transactor <- doobieTransactor(db).flatMapError(logErrorManaged("Failed to create doobie transactor"))
      actorSystem <- ZManaged.make(UIO(ActorSystem("OreJobs"))) { system =>
        ZIO
          .fromFuture(ec => system.terminate().map(identity)(ec))
          .flatMapError(logErrorUIO("Error when stopping actor system"))
          .ignore
      }
      materializer <- ZManaged
        .makeEffect(ActorMaterializer()(actorSystem))(_.shutdown())
        .flatMapError(logErrorManaged("Failed to create materializer"))
      taskService = new OreModelService(db, transactor)
      uioService  = taskService.mapK(Lambda[Task ~> UIO](task => task.orDie))
      jobsConfig <- ZManaged.fromEither(OreJobsConfig.load).flatMapError { e =>
        ???
      }
      akkaDiscourseClient <- jobsConfig.forums.api.pipe { cfg =>
        implicit val impSystem: ActorSystem    = actorSystem
        implicit val impMat: ActorMaterializer = materializer
        ZManaged
          .fromEffect(
            AkkaDiscourseApi[Task](
              AkkaDiscourseSettings(
                cfg.key,
                cfg.admin,
                jobsConfig.forums.baseUrl,
                cfg.breaker.maxFailures,
                cfg.breaker.reset,
                cfg.breaker.timeout
              )
            )
          )
          .flatMapError(logErrorManaged("Failed to create forums client"))
      }
      oreDiscourse = jobsConfig.forums.pipe { cfg =>
        implicit val runtime: Runtime[Environment] = this
        implicit val service: ModelService[Task]   = taskService
        implicit val impConfig: OreJobsConfig      = jobsConfig
        new OreDiscourseApiEnabled[Task](
          akkaDiscourseClient,
          cfg.categoryDefault,
          cfg.categoryDeleted,
          resources.resolve("discourse/project_topic.md"),
          resources.resolve("discourse/version_post.md"),
          cfg.api.admin
        )
      }
      _ <- runApp(db.source.maxConnections.getOrElse(32))
        .provideSome[Environment] { env =>
          new Db with Discourse with Config with Clock with Console with System with Random with Blocking {
            override val service: ModelService[UIO]       = uioService
            override val discourse: OreDiscourseApi[Task] = oreDiscourse
            override val config: OreJobsConfig            = jobsConfig
            override val random: Random.Service[Any]      = env.random
            override val clock: Clock.Service[Any]        = env.clock
            override val blocking: Blocking.Service[Any]  = env.blocking
            override val system: System.Service[Any]      = env.system
            override val console: Console.Service[Any]    = env.console
          }
        }
    } yield 0).catchAll(ZManaged.succeed)
  }

  type ExpandedEnvironment = Environment with Db with Discourse with Config

  private def runApp(maxConnections: Int): ZManaged[ExpandedEnvironment, Nothing, Unit] =
    ZManaged.foreachPar_(0 until maxConnections) { _ =>
      ZManaged.fromEffect(JobsProcessor.fiber)
    }

  private def logErrorManaged(msg: String)(e: Throwable): ZManaged[Any, Nothing, Int] = {
    Logger.error(msg, e)
    ZManaged.succeed(-1)
  }

  private def logErrorUIO(msg: String)(e: Throwable): UIO[Unit] = {
    Logger.error(msg, e)
    ZIO.succeed(())
  }

  private def doobieTransactor(db: SlickDb): ZManaged[Environment, Throwable, Transactor.Aux[Task, JdbcDataSource]] = {
    for {
      connectEC <- {
        implicit val runtime: DefaultRuntime = this
        ExecutionContexts.fixedThreadPool[Task](32).toManaged
      }
      blocker <- ZManaged.fromEffect(blocking.blockingExecutor)
    } yield {
      Transactor[Task, JdbcDataSource](
        db.source,
        source => {
          import zio.blocking._
          val acquire = Task(source.createConnection()).on(connectEC)

          def release(c: Connection) = effectBlocking(c.close()).provide(Environment)

          Resource.make(acquire)(release)
        },
        KleisliInterpreter[Task](Blocker.liftExecutionContext(blocker.asEC)).ConnectionInterpreter,
        Strategy.default
      )
    }
  }

  private def slickDb: ZManaged[Any, Throwable, SlickDb] =
    ZManaged.make(ZIO(Database.forConfig("jobs-db")))(a => UIO(a.close()))
}
