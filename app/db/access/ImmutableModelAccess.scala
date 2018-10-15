package db.access

import scala.concurrent.ExecutionContext

import db.impl.OrePostgresDriver.api._
import db.{Model, ModelFilter, ModelQuery, ModelService}

/**
  * An immutable version of [[ModelAccess]].
  *
  * @param service    ModelService instance
  * @param baseFilter Base filter
  * @tparam M         Model type
  */
case class ImmutableModelAccess[M <: Model: ModelQuery](
    override val service: ModelService,
    override val baseFilter: ModelFilter[M] = ModelFilter[M]()
) extends ModelAccess[M](service, baseFilter) {

  def this(mutable: ModelAccess[M]) = this(mutable.service, mutable.baseFilter)

  override def add(model: M)(implicit ec: ExecutionContext) = throw new UnsupportedOperationException
  override def remove(model: M)                             = throw new UnsupportedOperationException
  override def removeAll(filter: M#T => Rep[Boolean])       = throw new UnsupportedOperationException

}

object ImmutableModelAccess {

  def apply[M <: Model: ModelQuery](mutable: ModelAccess[M]): ImmutableModelAccess[M] =
    new ImmutableModelAccess(mutable)

}
