package util

import scala.concurrent.{ExecutionContext, Future}

import play.api.inject.ApplicationLifecycle

import cats.effect.{Bracket, IO, Resource}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import zio.Task

object TaskUtils {

  def logCallback[A](msg: => String, logger: LoggerTakingImplicit[A])(
      implicit imp: A
  ): Either[Throwable, _] => IO[Unit] = {
    case Right(_) => IO(())
    case Left(e)  => IO(logger.error(msg, e))
  }

  def logCallbackNoMDC[A](msg: => String, logger: Logger): Either[Throwable, _] => IO[Unit] = {
    case Right(_) => IO(())
    case Left(e)  => IO(logger.error(msg, e))
  }

  def logCallbackUnitNoMDC[A](msg: => String, logger: Logger): Either[Throwable, _] => Unit = {
    case Right(_) =>
    case Left(e)  => logger.error(msg, e)
  }

  def applicationResource[A](runtime: zio.Runtime[Any], resource: Resource[Task, A])(
      lifecycle: ApplicationLifecycle
  )(implicit bracket: Bracket[Task, Throwable]): A = {
    runtime.unsafeRunSync(resource.allocated).toEither match {
      case Right((a, finalize)) =>
        lifecycle.addStopHook { () =>
          runtime.unsafeRunToFuture(finalize)
        }

        a
      case Left(error) => throw error
    }
  }
}
