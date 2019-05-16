package ore.db.impl.table.common

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.Named

/**
  * A table column to represent a models's name.
  *
  * @tparam M Model type
  */
trait NameColumn[M <: Named] extends ModelTable[M] {

  /**
    * The model's name column.
    *
    * @return Model name column
    */
  def name = column[String]("name")

}
