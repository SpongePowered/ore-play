package db

import slick.lifted.Query

trait ModelQuery[A <: Model] {
  def baseQuery: Query[A#T, A, Seq]
}
object ModelQuery {
  def apply[A <: Model](implicit query: ModelQuery[A]): ModelQuery[A] = query

  def from[A <: Model](query: Query[A#T, A, Seq]): ModelQuery[A] = new ModelQuery[A] {
    override def baseQuery = query
  }
}
