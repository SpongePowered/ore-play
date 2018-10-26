package db

import scala.language.implicitConversions

import db.access.ModelAccess

/**
  * Represents something that provides access to a ModelTable.
  *
  * @tparam M Model
  */
trait ModelBase[M <: Model] {

  /** The [[ModelService]] to retrieve the model */
  def service: ModelService

  /**
    * Provides access to the ModelTable.
    *
    * @return ModelAccess
    */
  def access(implicit query: ModelQuery[M]): ModelAccess[M] = this.service.access[M]()

}

object ModelBase {
  implicit def unwrap[M <: Model: ModelQuery](base: ModelBase[M]): ModelAccess[M] = base.access
}
