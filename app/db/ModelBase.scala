package db

import db.access.ModelAccess

import scala.language.implicitConversions

/**
  * Represents something that provides access to a ModelTable.
  *
  * @tparam M Model
  */
trait ModelBase[M <: Model] {

  /** The [[Model]] that this provides access to */
  def modelClass: Class[M]
  /** The [[ModelService]] to retrieve the model */
  def service: ModelService

  /**
    * Provides access to the ModelTable.
    *
    * @return ModelAccess
    */
  def access: ModelAccess[M] = this.service.access[M](this.modelClass)

}

object ModelBase {
  implicit def unwrap[M <: Model](base: ModelBase[M]): ModelAccess[M] = base.access
}
