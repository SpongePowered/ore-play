package db

import db.table.ModelTable

import slick.lifted.Query

trait DbModelCompanion[M] {
  type T <: ModelTable[M]

  def baseQuery: Query[T, Model[M], Seq]

  def asDbModel(model: M, id: ObjId[M], time: ObjTimestamp): Model[M]
}
object DbModelCompanion {
  type Aux[M, T0 <: ModelTable[M]] = DbModelCompanion[M] { type T = T0 }
}

abstract class DbModelCompanionPartial[M, T0 <: ModelTable[M]](val baseQuery: Query[T0, Model[M], Seq])
    extends DbModelCompanion[M] {
  type T = T0
}
abstract class DefaultDbModelCompanion[M, T0 <: ModelTable[M]](baseQuery: Query[T0, Model[M], Seq])
    extends DbModelCompanionPartial(baseQuery) {
  override def asDbModel(model: M, id: ObjId[M], time: ObjTimestamp): Model[M] = Model(id, time, model)
}
