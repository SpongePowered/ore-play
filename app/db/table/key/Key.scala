package db.table.key

import db.Model
import db.impl.OrePostgresDriver.api._
import slick.jdbc.JdbcType

/**
  * Maps a [[Model]]'s field to the corresponding [[db.table.ModelTable]] column.
  *
  * @tparam M Model type
  */
class Key[M <: Model, A](val ref: M#T => Rep[A], val getter: M => A)(implicit mapper: JdbcType[A]) {

  def update(model: M) = model.service.set(model, this.ref, getter(model))

}
