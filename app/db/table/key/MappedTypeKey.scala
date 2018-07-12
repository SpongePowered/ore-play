package db.table.key

import db.Model
import db.impl.OrePostgresDriver.api._
import db.table.MappedType

class MappedTypeKey[M <: Model, A <: MappedType[A]](override val ref: M#T => Rep[A],
                                                         override val getter: M => A)
                                                         extends Key[M, A](ref, getter)(mapper = null) {

  override def update(model: M) = model.service.setMappedType(model, this.ref, this.getter(model))

}
