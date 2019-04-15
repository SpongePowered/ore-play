package util.instances

import scala.concurrent.ExecutionContext

import cats.syntax.either._
import cats.{MonadError, StackSafeMonad}
import slick.dbio.DBIO

trait DBIOInstances {

  //TODO: Traverse instance
  implicit def dbioInstance(implicit ec: ExecutionContext): MonadError[DBIO, Throwable] =
    new MonadError[DBIO, Throwable] with StackSafeMonad[DBIO] {
      override def pure[A](x: A): DBIO[A]                               = DBIO.successful(x)
      override def flatMap[A, B](fa: DBIO[A])(f: A => DBIO[B]): DBIO[B] = fa.flatMap(f)
      override def raiseError[A](e: Throwable): DBIO[A]                 = DBIO.failed(e)
      override def handleErrorWith[A](fa: DBIO[A])(f: Throwable => DBIO[A]): DBIO[A] =
        fa.asTry.flatMap(_.toEither.bimap(f, pure).merge)

      override def flatten[A](ffa: DBIO[DBIO[A]]): DBIO[A]                          = ffa.flatten
      override def map[A, B](fa: DBIO[A])(f: A => B): DBIO[B]                       = fa.map(f)
      override def product[A, B](fa: DBIO[A], fb: DBIO[B]): DBIO[(A, B)]            = fa.zip(fb)
      override def map2[A, B, C](fa: DBIO[A], fb: DBIO[B])(f: (A, B) => C): DBIO[C] = fa.zipWith(fb)(f)
    }
}
