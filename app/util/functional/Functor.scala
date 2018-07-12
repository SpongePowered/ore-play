package util.functional

import scala.language.higherKinds

trait Functor[F[_]] {

  def map[A, B](fa: F[A])(f: A => B): F[B]

  def as[A, B](fa: F[A], b: B): F[B] = map(fa)(_ => b)

  def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => (a, f(a)))

  def tupleLeft[A, B](fa: F[A], b: B): F[(B, A)] = map(fa)(a => (b, a))

  def tupleRight[A, B](fa: F[A], b: B): F[(A, B)] = map(fa)(a => (a, b))

}
