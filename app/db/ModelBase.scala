package db

import scala.language.implicitConversions

import db.access.ModelAccess

/**
  * Represents something that provides access to a ModelTable.
  *
  * @tparam M0 Model
  */
trait ModelBase[M0 <: Model { type M = M0 }] {

  /** The [[ModelService]] to retrieve the model */
  def service: ModelService

  /**
    * Provides access to the ModelTable.
    *
    * @return ModelAccess
    */
  def access(implicit query: ModelQuery[M0]): ModelAccess[M0] = this.service.access[M0]()

}

object ModelBase {
  implicit def unwrap[M0 <: Model { type M = M0 }: ModelQuery](base: ModelBase[M0]): ModelAccess[M0] = base.access
}
