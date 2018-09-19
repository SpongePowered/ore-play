package ore.user

import scala.collection.immutable

import enumeratum.values._

sealed abstract class Prompt(val value: Int, val titleId: String, val messageId: String) extends IntEnumEntry
object Prompt extends IntEnum[Prompt] {

  val values: immutable.IndexedSeq[Prompt] = findValues

  case object ChangeAvatar extends Prompt(0, "prompt.changeAvatar.title", "prompt.changeAvatar.message")
  case object PGP          extends Prompt(1, "prompt.pgp.title", "prompt.pgp.message")
}
