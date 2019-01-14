package db

import slick.lifted.Query

trait ModelQuery[A <: Model] {
  def baseQuery: Query[A#T, A, Seq]

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id       ID to set
    * @param theTime  Timestamp
    * @return         Copy of model
    */
  def copyWith(model: A)(id: ObjId[A], theTime: ObjTimestamp): A
}
object ModelQuery {
  def apply[A <: Model](implicit query: ModelQuery[A]): ModelQuery[A] = query

  def from[A <: Model](query: Query[A#T, A, Seq], copy: (A, ObjId[A], ObjTimestamp) => A): ModelQuery[A] =
    new ModelQuery[A] {
      override def baseQuery = query

      override def copyWith(model: A)(id: ObjId[A], theTime: ObjTimestamp): A = copy(model, id, theTime)
    }
}
