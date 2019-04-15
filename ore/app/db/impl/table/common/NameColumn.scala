package db.impl.table.common

import db.impl.OrePostgresDriver.api._
import db.impl.common.Named

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
