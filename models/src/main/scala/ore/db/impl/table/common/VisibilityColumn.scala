package ore.db.impl.table.common

import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.Visibility

/**
  * Represents a column in a [[ModelTable]] representing the visibility of the
  * model.
  *
  * @tparam M Model type
  */
trait VisibilityColumn[M] extends ModelTable[M] {

  /**
    * Column definition of visibility. True if visible.
    *
    * @return Visibility column
    */
  def visibility = column[Visibility]("visibility")

}
