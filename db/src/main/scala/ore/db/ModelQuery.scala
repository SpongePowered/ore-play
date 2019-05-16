package ore.db

import slick.lifted.Query

trait ModelQuery[A] {
  type T <: OreProfile#ModelTable[A]
  val companion: ModelCompanion.Aux[A, T]

  def baseQuery: Query[T, Model[A], Seq] = companion.baseQuery

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id       ID to set
    * @param theTime  Timestamp
    * @return         Copy of model
    */
  def asDbModel(model: A)(id: ObjId[A], theTime: ObjInstant): Model[A] = companion.asDbModel(model, id, theTime)
}
object ModelQuery {
  type Aux[A, T0 <: OreProfile#ModelTable[A]] = ModelQuery[A] { type T = T0 }

  def apply[A, T <: OreProfile#ModelTable[A]](implicit query: ModelQuery.Aux[A, T]): ModelQuery.Aux[A, T] = query

  def from[A, T0 <: OreProfile#ModelTable[A]](model: ModelCompanion.Aux[A, T0]): ModelQuery.Aux[A, T0] =
    new ModelQuery[A] {
      type T = T0
      override val companion: ModelCompanion.Aux[A, T] = model
    }
}
