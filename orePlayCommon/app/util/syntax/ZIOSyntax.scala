package util.syntax

import scala.language.implicitConversions

import cats.data.{EitherT, OptionT}
import zio.{IO, UIO, ZIO}

trait ZIOSyntax {

  implicit def eitherTSyntax[E, A](e: EitherT[UIO, E, A]): ZIOSyntax.EitherTSyntax[E, A] =
    new ZIOSyntax.EitherTSyntax(e)
  implicit def optionTSyntax[A](o: OptionT[UIO, A]): ZIOSyntax.OptionTSyntax[A]    = new ZIOSyntax.OptionTSyntax(o)
  implicit def zioSyntax[R, E, A](zio: ZIO[R, E, A]): ZIOSyntax.ZIOSyntax[R, E, A] = new ZIOSyntax.ZIOSyntax(zio)

}
object ZIOSyntax {

  class EitherTSyntax[E, A](private val e: EitherT[UIO, E, A]) extends AnyVal {

    def toZIO: IO[E, A] = e.value.absolve
  }

  class OptionTSyntax[A](private val o: OptionT[UIO, A]) extends AnyVal {

    def toZIO: IO[Unit, A] = o.value.get

    def toZIOWithError[E](err: E): IO[E, A] = new ZIOSyntax(o.value.get).constError(err)
  }

  class ZIOSyntax[R, E, A](private val zio: ZIO[R, E, A]) extends AnyVal {

    def constError[E2](err: E2): ZIO[R, E2, A] = zio.mapError(_ => err)
  }
}
