package ore

import java.sql.Connection

import scala.concurrent.Await
import scala.util.chaining._

import ore.db.ModelService
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.service.OreModelService
import ore.discourse.AkkaDiscourseApi.AkkaDiscourseSettings
import ore.discourse.{AkkaDiscourseApi, Discourse, DiscourseApi, OreDiscourseApi, OreDiscourseApiEnabled}
import ore.models.Job

import akka.actor.{ActorSystem, Terminated}
import akka.stream.scaladsl.{Sink, StreamConverters}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
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
import zio.duration.Duration
import zio.interop.catz._
import zio.random.Random
import zio.system.System

object OreJobProcessorMain extends zio.ManagedApp {
  private val Logger = scalalogging.Logger("OreJobsMain")

  type SlickDb = OrePostgresDriver.backend.DatabaseDef

  override def run(args: List[String]): ZManaged[ZEnv, Nothing, Int] = {
    def log(f: scalalogging.Logger => Unit): ZManaged[Any, Nothing, Unit] = ZManaged.fromEffect(UIO(f(Logger)))

    (for {
      _          <- log(_.info("Ore jobs processing starting"))
      db         <- createSlickDb
      transactor <- createDoobieTransactor(db)
      taskService = new OreModelService(db, transactor)
      uioService  = taskService.mapK(Lambda[Task ~> UIO](task => task.orDie))
      _                   <- log(_.info("Created DB system"))
      actorSystem         <- createActorSystem
      _                   <- log(_.info("Created Akka system"))
      jobsConfig          <- createConfig
      _                   <- log(_.info("Loaded config"))
      akkaDiscourseClient <- createDiscourseApi(jobsConfig)(actorSystem)
      oreDiscourse = createOreDiscourse(akkaDiscourseClient)(jobsConfig, taskService, actorSystem)
      _ <- log(_.info("Init finished. Starting"))
      _ <- runApp(db.source.maxConnections.getOrElse(32))
        .provideSome[ZEnv](createExpandedEnvironment(uioService, oreDiscourse, jobsConfig))
    } yield 0).catchAll(ZManaged.succeed)
  }

  type ExpandedEnvironment = Db with Discourse with Config with ZEnv

  private def createExpandedEnvironment(
      serviceObj: ModelService[UIO],
      discourseObj: OreDiscourseApi[Task],
      configObj: OreJobsConfig
  )(env: ZEnv): ExpandedEnvironment =
    new Db with Discourse with Config with Clock with Console with System with Random with Blocking {
      override val random: Random.Service[Any]      = env.random
      override val system: System.Service[Any]      = env.system
      override val config: OreJobsConfig            = configObj
      override val console: Console.Service[Any]    = env.console
      override val clock: Clock.Service[Any]        = env.clock
      override val discourse: OreDiscourseApi[Task] = discourseObj
      override val service: ModelService[UIO]       = serviceObj
      override val blocking: Blocking.Service[Any]  = env.blocking
    }

  private def runApp(maxConnections: Int): ZManaged[ExpandedEnvironment, Nothing, Unit] =
    ZManaged.environment[ExpandedEnvironment].flatMap { env =>
      implicit val service: ModelService[UIO] = env.service

      val checkAndCreateFibers = for {
        awaitingJobs <- ModelView.now(Job).count(_.state === (Job.JobState.NotStarted: Job.JobState))
        _            <- UIO(Logger.debug(s"Found $awaitingJobs unstarted jobs"))
        _ <- if (awaitingJobs > 0) {
          val fibers = ZIO.foreachPar_(0 until math.max(1, math.min(awaitingJobs, maxConnections - 3))) { _ =>
            JobsProcessor.fiber.sandbox
          }

          fibers.catchAll { cause =>
            Logger.error(s"A fiber died while processing it's jobs\n${cause.prettyPrint}")
            ZIO.unit
          }
        } else ZIO.unit
      } yield ()

      val schedule = Schedule.spaced(Duration.fromScala(env.config.jobs.checkInterval))

      ZManaged.fromEffect(
        checkAndCreateFibers.sandbox
          .catchAll { cause =>
            Logger.error(s"Encountered an error while checking if there are more jobs\n${cause.prettyPrint}")
            ZIO.unit
          }
          .repeat(schedule)
          .unit
      )
    }

  private def logErrorManaged(msg: String)(e: Throwable): ZManaged[Any, Nothing, Int] = {
    Logger.error(msg, e)
    ZManaged.succeed(-1)
  }

  private def logErrorUIO(msg: String)(e: Throwable): UIO[Unit] = {
    Logger.error(msg, e)
    ZIO.succeed(())
  }

  private def createSlickDb: ZManaged[Any, Int, SlickDb] =
    ZManaged
      .makeEffect(Database.forConfig("jobs-db", classLoader = this.getClass.getClassLoader))(_.close())
      .flatMapError(logErrorManaged("Failed to connect to db"))

  private def createDoobieTransactor(db: SlickDb): ZManaged[ZEnv, Int, Transactor[Task]] = {
    (for {
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

          def release(c: Connection) = effectBlocking(c.close()).provide(environment)

          Resource.make(acquire)(release)
        },
        KleisliInterpreter[Task](Blocker.liftExecutionContext(blocker.asEC)).ConnectionInterpreter,
        Strategy.default
      )
    }).flatMapError(logErrorManaged("Failed to create doobie transactor"))
  }

  private def createActorSystem: ZManaged[Any, Nothing, ActorSystem] =
    ZManaged.make(UIO(ActorSystem("OreJobs"))) { system =>
      val terminate: ZIO[Any, Unit, Terminated] = ZIO
        .fromFuture(ec => system.terminate().map(identity)(ec))
        .flatMapError(logErrorUIO("Error when stopping actor system"))
      terminate.ignore
    }

  private def createConfig: ZManaged[Any, Int, OreJobsConfig] =
    ZManaged.fromEither(OreJobsConfig.load).flatMapError { es =>
      Logger.error(
        s"Failed to load config:${es.toList.map(e => s"${e.description} -> ${e.location.fold("")(_.description)}").mkString("\n  ", "\n  ", "")}"
      )
      ZManaged.succeed(-1)
    }

  private def createDiscourseApi(
      config: OreJobsConfig
  )(implicit system: ActorSystem): ZManaged[Any, Int, AkkaDiscourseApi[Task]] =
    config.discourse.api.pipe { cfg =>
      ZManaged
        .fromEffect(
          AkkaDiscourseApi[Task](
            AkkaDiscourseSettings(
              cfg.key,
              cfg.admin,
              config.discourse.baseUrl,
              cfg.breaker.maxFailures,
              cfg.breaker.reset,
              cfg.breaker.timeout
            )
          )
        )
        .flatMapError(logErrorManaged("Failed to create forums client"))
    }

  private def createOreDiscourse(
      discourseClient: DiscourseApi[Task]
  )(implicit config: OreJobsConfig, service: ModelService[Task], system: ActorSystem): OreDiscourseApiEnabled[Task] =
    config.discourse.pipe { cfg =>
      implicit val runtime: Runtime[ZEnv] = this
      def readFile(file: String) =
        Await.result(
          StreamConverters
            .fromInputStream(
              () => this.getClass.getClassLoader.getResourceAsStream(file)
            )
            .fold(ByteString.empty)(_ ++ _)
            .map(_.utf8String)
            .runWith(Sink.head),
          scala.concurrent.duration.Duration.Inf
        )

      new OreDiscourseApiEnabled[Task](
        discourseClient,
        cfg.categoryDefault,
        cfg.categoryDeleted,
        readFile("discourse/project_topic.md"),
        readFile("discourse/version_post.md"),
        cfg.api.admin
      )
    }
}
