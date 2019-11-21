package util.fp

import cats.arrow.FunctionK
import cats.data.Tuple2K
import cats.tagless.ApplyK
import cats.~>
import shapeless.Const

trait ApplicativeK[F[_[_]]] extends ApplyK[F] {
  def pure[A[_]](a: Const[Unit]#λ ~> A): F[A]

  def unit: F[Const[Unit]#λ] = pure(FunctionK.id)

  def apK[A[_], B[_]](ff: F[λ[C => A[C] => B[C]]])(fa: F[A]): F[B] =
    map2K(ff, fa)(λ[Tuple2K[λ[C => A[C] => B[C]], A, *] ~> B](fa => fa.first(fa.second)))

  override def mapK[A[_], B[_]](af: F[A])(fk: A ~> B): F[B] =
    apK(pure(λ[Const[Unit]#λ ~> λ[C => A[C] => B[C]]](_ => fk.apply)))(af)
}
