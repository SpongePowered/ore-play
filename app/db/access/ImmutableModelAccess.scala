package db.access

import db.impl.OrePostgresDriver.api._
import db.{Model, ModelFilter, ModelService}

import scala.concurrent.ExecutionContext

/**
  * An immutable version of [[ModelAccess]].
  *
  * @param service    ModelService instance
  * @param modelClass Model class
  * @param baseFilter Base filter
  * @tparam M         Model type
  */
case class ImmutableModelAccess[M <: Model]
(override val service: ModelService,
 override val modelClass: Class[M],
 override val baseFilter: ModelFilter[M] = ModelFilter[M]())
  extends ModelAccess[M](service, modelClass, baseFilter) {

  def this(mutable: ModelAccess[M]) = this(mutable.service, mutable.modelClass, mutable.baseFilter)

  override def add(model: M)(implicit ec: ExecutionContext) = throw new UnsupportedOperationException
  override def remove(model: M) = throw new UnsupportedOperationException
  override def removeAll(filter: M#T => Rep[Boolean]) = throw new UnsupportedOperationException

}

object ImmutableModelAccess {

  def apply[M <: Model](mutable: ModelAccess[M]): ImmutableModelAccess[M]
  = new ImmutableModelAccess(mutable)

}
