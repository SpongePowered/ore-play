package models.project

import scala.collection.immutable

import db.ModelFilter
import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Hideable

import enumeratum.values._

sealed abstract class Visibility(
    val value: Int,
    val nameKey: String,
    val showModal: Boolean,
    val cssClass: String
) extends IntEnumEntry
object Visibility extends IntEnum[Visibility] {

  val values: immutable.IndexedSeq[Visibility] = findValues

  case object Public        extends Visibility(1, "public", showModal = false, "")
  case object New           extends Visibility(2, "new", showModal = false, "project-new")
  case object NeedsChanges  extends Visibility(3, "needsChanges", showModal = true, "striped project-needsChanges")
  case object NeedsApproval extends Visibility(4, "needsApproval", showModal = false, "striped project-needsChanges")
  case object SoftDelete    extends Visibility(5, "softDelete", showModal = true, "striped project-hidden")

  def isPublic(visibility: Visibility): Boolean = visibility == Public || visibility == New

  def isPublicFilter[H <: Hideable]: H#T => Rep[Boolean] =
    ModelFilter[H](_.visibility === (Public: Visibility)) || ModelFilter[H](_.visibility === (New: Visibility))
}
