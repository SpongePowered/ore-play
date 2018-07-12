package util.functional

import scala.language.higherKinds
import util.syntax._

case class OptionT[F[_], A](value: F[Option[A]]) {

  def isEmpty(implicit F: Functor[F]): F[Boolean] = value.map(_.isEmpty)

  def isDefined(implicit F: Functor[F]): F[Boolean] = value.map(_.isDefined)

  def getOrElse[B >: A](default: => B)(implicit F: Functor[F]): F[B] = value.map(_.getOrElse(default))

  def getOrElseF[B >: A](default: => F[B])(implicit F: Monad[F]): F[B] =
    value.flatMap(_.fold(default)(F.pure))

  def map[B](f: A => B)(implicit F: Functor[F]): OptionT[F, B] = OptionT(value.map(_.map(f)))

  def fold[B](ifEmpty: => B)(f: A => B)(implicit F: Functor[F]): F[B] = value.map(_.fold(ifEmpty)(f))

  def cata[B](ifEmpty: => B, f: A => B)(implicit F: Functor[F]): F[B] = fold(ifEmpty)(f)

  def semiFlatMap[B](f: A => F[B])(implicit F: Monad[F]): OptionT[F, B] = flatMap(a => OptionT.liftF(f(a)))

  def flatMap[B](f: A => OptionT[F, B])(implicit F: Monad[F]): OptionT[F, B] = flatMapF(a => f(a).value)

  def flatMapF[B](f: A => F[Option[B]])(implicit F: Monad[F]): OptionT[F, B] =
    OptionT(value.flatMap(_.fold(F.pure[Option[B]](None))(f)))

  def transform[B](f: Option[A] => Option[B])(implicit F: Functor[F]): OptionT[F, B] = OptionT(value.map(f))

  def subflatMap[B](f: A => Option[B])(implicit F: Functor[F]): OptionT[F, B] = transform(_.flatMap(f))

  def filter(f: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = OptionT(value.map(_.filter(f)))

  def withFilter(f: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = filter(f)

  def filterNot(f: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = OptionT(value.map(_.filterNot(f)))

  def contains[A1 >: A](elem: A1)(implicit F: Functor[F]): F[Boolean] = value.map(_.contains(elem))

  def exists(f: A => Boolean)(implicit F: Functor[F]): F[Boolean] = value.map(_.exists(f))

  def forall(f: A => Boolean)(implicit F: Functor[F]): F[Boolean] = value.map(_.forall(f))

  def collect[B](f: PartialFunction[A, B])(implicit F: Functor[F]): OptionT[F, B] =
    OptionT(value.map(_.collect(f)))

  def orElse(alternative: OptionT[F, A])(implicit F: Monad[F]): OptionT[F, A] =
    orElseF(alternative.value)

  def orElseF(default: => F[Option[A]])(implicit F: Monad[F]): OptionT[F, A] =
    OptionT(
      value.flatMap {
        case s @ Some(_) => F.pure(s)
        case None        => default
      }
    )

  def toRight[X](left: => X)(implicit F: Functor[F]): EitherT[F, X, A] = EitherT(cata(Left(left), Right.apply))

  def toLeft[X](right: => X)(implicit F: Functor[F]): EitherT[F, A, X] = EitherT(cata(Right(right), Left.apply))
}
object OptionT {

  //To overcome type interference
  final class PurePartiallyApplied[F[_]](val b: Boolean = true) extends AnyVal {
    def apply[A](value: A)(implicit F: Applicative[F]): OptionT[F, A] =
      OptionT(F.pure(Some(value)))
  }

  def pure[F[_]]: PurePartiallyApplied[F] = new PurePartiallyApplied[F]

  def some[F[_]]: PurePartiallyApplied[F] = pure

  def none[F[_], A](implicit F: Applicative[F]): OptionT[F, A] = OptionT(F.pure(None))

  def fromOption[F[_]]: FromOptionPartiallyApplied[F] = new FromOptionPartiallyApplied

  //To overcome type interference
  final class FromOptionPartiallyApplied[F[_]](val b: Boolean = true ) extends AnyVal {
    def apply[A](value: Option[A])(implicit F: Applicative[F]): OptionT[F, A] =
      OptionT(F.pure(value))
  }

  def liftF[F[_], A](fa: F[A])(implicit F: Functor[F]): OptionT[F, A] = OptionT(fa.map(Some(_)))

  implicit def optionTMonad[F[_]: Monad]: Monad[({type λ[A] = OptionT[F, A]})#λ] = new Monad[({type λ[A] = OptionT[F, A]})#λ] {
    override def flatMap[A, B](fa: OptionT[F, A])(f: A => OptionT[F, B]): OptionT[F, B] = fa.flatMap(f)
    override def pure[A](a: A): OptionT[F, A] = OptionT.pure[F](a)
    override def map[A, B](fa: OptionT[F, A])(f: A => B): OptionT[F, B] = fa.map(f)
  }
}