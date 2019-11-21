package util.fp

import scala.language.implicitConversions

import cats.{Monoid, ~>}
import shapeless.Const

trait FoldableK[F[_[_]]] {

  def foldLeftK[A[_], B](fa: F[A], b: B)(f: B => A ~> Const[B]#λ): B

  def foldMapK[A[_], B](fa: F[A])(f: A ~> Const[B]#λ)(implicit B: Monoid[B]): B =
    foldLeftK(fa, B.empty)(b => λ[A ~> Const[B]#λ](a => B.combine(b, f(a))))
}
object FoldableK {

  class FOps[F[_[_]], A[_]](private val fa: F[A]) extends AnyVal {

    def foldLeftK[B](b: B)(f: B => A ~> Const[B]#λ)(implicit F: FoldableK[F]): B = F.foldLeftK(fa, b)(f)

    def foldMapK[B: Monoid](f: A ~> Const[B]#λ)(implicit F: FoldableK[F]): B = F.foldMapK(fa)(f)
  }

  trait ToFoldableKOps {
    implicit def foldableKToFOps[F[_[_]], A[_]](fa: F[A]): FOps[F, A] = new FOps(fa)
  }
}
