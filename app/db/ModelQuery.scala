package db

import slick.lifted.Query

trait ModelQuery[A] {
  val companion: ModelCompanion[A]

  def baseQuery: Query[companion.T, Model[A], Seq] = companion.baseQuery

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id       ID to set
    * @param theTime  Timestamp
    * @return         Copy of model
    */
  def asDbModel(model: A)(id: ObjId[A], theTime: ObjTimestamp): Model[A] = companion.asDbModel(model, id, theTime)
}
object ModelQuery {
  def apply[A](implicit query: ModelQuery[A]): ModelQuery[A] = query

  def from[A](model: ModelCompanion[A]): ModelQuery[A] =
    new ModelQuery[A] {
      override val companion: ModelCompanion[A] = model
    }
}
