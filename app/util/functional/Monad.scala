package util.functional

import scala.language.higherKinds

trait Monad[F[_]] extends Applicative[F] {

  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = flatMap(ff)(f => map(fa)(f))

  def flatten[A](ffa: F[F[A]]): F[A] = flatMap(ffa)(fa => fa)
}