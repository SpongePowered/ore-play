package util

import cats.syntax.all._
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
}
