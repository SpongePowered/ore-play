package ore.rest

import scala.collection.immutable

import enumeratum.values._

sealed abstract class ProjectApiKeyType(val value: Int, val name: String) extends IntEnumEntry
object ProjectApiKeyType extends IntEnum[ProjectApiKeyType] {

  val values: immutable.IndexedSeq[ProjectApiKeyType] = findValues

  case object Deployment extends ProjectApiKeyType(0, "deployment")
}
