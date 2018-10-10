package models.project

import scala.collection.immutable

import db.ModelFilter
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Hideable
import ore.permission.{Permission, ReviewProjects}

import enumeratum.values._

sealed abstract class Visibility(
    val value: Int,
    val nameKey: String,
    val permission: Permission,
    val showModal: Boolean,
    val cssClass: String
) extends IntEnumEntry
object Visibility extends IntEnum[Visibility] {

  val values: immutable.IndexedSeq[Visibility] = findValues

  case object Public extends Visibility(1, "public", ReviewProjects, showModal = false, "")
  case object New    extends Visibility(2, "new", ReviewProjects, showModal = false, "project-new")
  case object NeedsChanges
      extends Visibility(3, "needsChanges", ReviewProjects, showModal = true, "striped project-needsChanges")
  case object NeedsApproval
      extends Visibility(4, "needsApproval", ReviewProjects, showModal = false, "striped project-needsChanges")
  case object SoftDelete extends Visibility(5, "softDelete", ReviewProjects, showModal = true, "striped project-hidden")

  def isPublic(visibility: Visibility): Boolean = visibility == Public || visibility == New

  def isPublicFilter[H <: Hideable]: ModelFilter[H] =
    ModelFilter[H](_.visibility === (Public: Visibility)) +||
      ModelFilter[H](_.visibility === (New: Visibility))
}
