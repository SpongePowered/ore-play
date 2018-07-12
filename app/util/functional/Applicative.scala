package util.functional

import scala.language.higherKinds

trait Applicative[F[_]] extends Functor[F] {

  def pure[A](a: A): F[A]

  def ap[A, B](ff: F[A => B])(fa: F[A]): F[B]

  override def map[A, B](fa: F[A])(f: A => B): F[B] = ap(pure(f))(fa)

  def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = ap(map(fa)(a => (b: B) => (a, b)))(fb)

  def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] = map(product(fa, fb))(f.tupled)

  def <*>[A, B](ff: F[A => B])(fa: F[A]): F[B] = ap(ff)(fa)

  def *>[A, B](fa: F[A])(fb: F[B]): F[B] = map2(fa, fb)((_, b) => b)

  def <*[A, B](fa: F[A])(fb: F[B]): F[A] = map2(fa, fb)((a, _) => a)
}
