package util.instances

import util.functional.Monad

trait EitherInstances {

  implicit def eitherInstance[L]: Monad[({ type λ[R] = Either[L, R] })#λ] = new Monad[({ type λ[R] = Either[L, R] })#λ] {
    override def flatMap[A, B](fa: Either[L, A])(f: A => Either[L, B]): Either[L, B] = fa.flatMap(f)
    override def pure[A](a: A): Either[L, A] = Right(a)
    override def map[A, B](fa: Either[L, A])(f: A => B): Either[L, B] = fa.map(f)
  }
}
