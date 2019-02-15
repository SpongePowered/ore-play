package db.impl.table.common

import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Hideable, VisibilityChange}
import db.table.ModelTable
import models.project.Visibility

/**
  * Represents a column in a [[ModelTable]] representing the visibility of the
  * model.
  *
  * @tparam M Model type
  */
trait VisibilityColumn[M <: Hideable] extends ModelTable[M] {

  /**
    * Column definition of visibility. True if visible.
    *
    * @return Visibility column
    */
  def visibility = column[Visibility]("visibility")

}
