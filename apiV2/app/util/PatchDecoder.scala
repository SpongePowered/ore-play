package util

import cats.Applicative
import cats.syntax.all._
import squeal.category._
import squeal.category.syntax.all._
import io.circe.{ACursor, Decoder}

trait PatchDecoder[A] {

  def decode(cursor: ACursor): Decoder.AccumulatingResult[Option[A]]
}
object PatchDecoder {

  def mkPath[A: Decoder](path: String*): PatchDecoder[A] = (cursor: ACursor) => {
    import cats.instances.either._
    import cats.instances.option._

    val cursorWithPath = path.foldLeft(cursor)(_.downField(_))

    val res = if (cursorWithPath.succeeded) Some(cursorWithPath.as[A]) else None

    res.sequence.toValidatedNel
  }

  def fromName[F[_[_]]: FunctorKC](
      fsd: F[Tuple2K[Const[String]#λ, Decoder]#λ]
  )(nameTransform: String => String): F[PatchDecoder] =
    fsd.mapKC(λ[Tuple2K[Const[String]#λ, Decoder]#λ ~>: PatchDecoder](t => mkPath(nameTransform(t._1))(t._2)))

  implicit val applicative: Applicative[PatchDecoder] = new Applicative[PatchDecoder] {
    override def pure[A](x: A): PatchDecoder[A] = ???

    override def ap[A, B](ff: PatchDecoder[A => B])(fa: PatchDecoder[A]): PatchDecoder[B] = ???
  }
}
