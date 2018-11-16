package db.impl.table.common

import db.Model
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Named
import db.table.ModelTable

/**
  * A table column to represent a [[Model]]'s name.
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
