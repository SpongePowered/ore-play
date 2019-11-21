package util.fp

import scala.language.implicitConversions

import cats.arrow.FunctionK
import cats.{Applicative, ~>}
import cats.tagless.FunctorK

trait TraverseK[F[_[_]]] extends FunctorK[F] with FoldableK[F] {

  def traverseK[G[_]: Applicative, A[_], B[_]](fa: F[A])(f: A ~> λ[C => G[B[C]]]): G[F[B]]

  def sequenceK[G[_]: Applicative, A[_]](fga: F[λ[C => G[A[C]]]]): G[F[A]] =
    traverseK(fga)(FunctionK.id)(Applicative[G])
}
object TraverseK {

  class FOps[F[_[_]], A[_]](private val fa: F[A]) extends AnyVal {

    def traverseK[G[_]: Applicative, B[_]](f: A ~> λ[C => G[B[C]]])(implicit F: TraverseK[F]): G[F[B]] =
      F.traverseK(fa)(f)
  }

  class FGOps[F[_[_]], G[_], A[_]](private val fga: F[λ[C => G[A[C]]]]) extends AnyVal {
    def sequenceK(implicit F: TraverseK[F], A: Applicative[G]): G[F[A]] = F.sequenceK(fga)
  }

  trait ToTraverseKOps {
    implicit def traverseKToFOps[F[_[_]], A[_]](fa: F[A]): FOps[F, A]                           = new FOps(fa)
    implicit def traverseKToFGOps[F[_[_]], G[_], A[_]](fga: F[λ[C => G[A[C]]]]): FGOps[F, G, A] = new FGOps(fga)
  }
}
