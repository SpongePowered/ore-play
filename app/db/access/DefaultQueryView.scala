package db.access

import db.DbRef
import db.impl.OrePostgresDriver.api._

import slick.lifted.Query

class DefaultQueryView[T, M](baseQuery: Query[T, M, Seq], idRef: T => Rep[DbRef[M]])
    extends ModelView[Query[T, M, Seq], Rep, T, M] {

  override def get(id: DbRef[M]): Query[T, M, Seq] =
    baseQuery.filter(t => idRef(t) === id)

  override def one: Query[T, M, Seq] = baseQuery.take(1)

  override def query: Query[T, M, Seq] = baseQuery

  override def size: Rep[Int] = baseQuery.length

  override def isEmpty: Rep[Boolean] = size === 0

  override def nonEmpty: Rep[Boolean] = size > 0

  override def exists(filter: T => Rep[Boolean]): Rep[Boolean] = baseQuery.filter(filter).exists

  override def forall(filter: T => Rep[Boolean]): Rep[Boolean] = !exists(!filter(_))

  override def find(filter: T => Rep[Boolean]): Query[T, M, Seq] = baseQuery.filter(filter).take(1)

  override def count(predicate: T => Rep[Boolean]): Rep[Int] = baseQuery.filter(predicate).length

  override def modifyingQuery(
      f: Query[T, M, Seq] => Query[T, M, Seq]
  ): DefaultQueryView[T, M] = new DefaultQueryView[T, M](f(baseQuery), idRef)
}
object DefaultQueryView {
  implicit val defaultQueryViewIsQueryView: QueryView[DefaultQueryView] = new QueryView[DefaultQueryView] {
    override def modifyingView[T, M](fa: DefaultQueryView[T, M])(
        f: Query[T, M, Seq] => Query[T, M, Seq]
    ): DefaultQueryView[T, M] = fa.modifyingQuery(f)
  }
}
