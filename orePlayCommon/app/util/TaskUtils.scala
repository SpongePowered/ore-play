package util

import cats.effect.IO
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}

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
}
