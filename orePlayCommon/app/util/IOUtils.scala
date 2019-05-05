package util

import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LoggerTakingImplicit

object IOUtils {

  def raceBoolean(fa: IO[Boolean], fb: IO[Boolean])(implicit cs: ContextShift[IO]): IO[Boolean] =
    IO.racePair(fa, fb).flatMap {
      case Left((false, fibB))  => fibB.join
      case Left((true, fibB))   => fibB.cancel.map(_ => true)
      case Right((fibA, false)) => fibA.join
      case Right((fibA, true))  => fibA.cancel.map(_ => true)
    }

  def logCallback[A](msg: => String, logger: LoggerTakingImplicit[A])(
      implicit imp: A
  ): Either[Throwable, _] => IO[Unit] = {
    case Right(_) => IO(())
    case Left(e)  => IO(logger.error(msg, e))
  }
}
