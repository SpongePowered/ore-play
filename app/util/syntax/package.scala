package util

import scala.language.higherKinds

import util.functional.{Applicative, Functor, Monad}

package object syntax extends ParallelSyntax {

  implicit class FunctorOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit F: Functor[F]): F[B] = F.map(fa)(f)

    def as[B](b: B)(implicit F: Functor[F]): F[B] = F.as(fa, b)

    def void(implicit F: Functor[F]): F[Unit] = F.as(fa, ())

    def fproduct[B](f: A => B)(implicit F: Functor[F]): F[(A, B)] = F.fproduct(fa)(f)

    def tupleLeft[B](b: B)(implicit F: Functor[F]): F[(B, A)] = F.tupleLeft(fa, b)

    def tupleRight[B](b: B)(implicit F: Functor[F]): F[(A, B)] = F.tupleRight(fa, b)
  }

  implicit class ApplicativeTupleOps[F[_], A, B](private val tfa: (F[A], F[B]))
      extends AnyVal {

    def product(implicit F: Applicative[F]): F[(A, B)] = F.product(tfa._1, tfa._2)

    def map2[C](f: (A, B) => C)(implicit F: Applicative[F]): F[C] = F.map2(tfa._1, tfa._2)(f)
  }

  implicit class ApplicativeOps[F[_], A](private val fa: F[A]) extends AnyVal {

    def *>[B](fb: F[B])(implicit F: Applicative[F]): F[B] = F.*>(fa)(fb)

    def <*[B](fb: F[B])(implicit F: Applicative[F]): F[A] = F.<*(fa)(fb)
  }

  implicit class ApplicativeApOps[F[_], A, B](private val ff: F[A => B])
      extends AnyVal {
    def <*>(fa: F[A])(implicit F: Applicative[F]): F[B] = F.ap(ff)(fa)
  }

  implicit class MonadOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def flatMap[B](f: A => F[B])(implicit F: Monad[F]): F[B] = F.flatMap(fa)(f)
    def flatTap[B](f: A => F[B])(implicit F: Monad[F]): F[A] = F.flatTap(fa)(f)
  }

  implicit class MonadFlattenOps[F[_], A](private val ffa: F[F[A]])
      extends AnyVal {
    def flatten(implicit F: Monad[F]): F[A] = F.flatten(ffa)
  }
}
