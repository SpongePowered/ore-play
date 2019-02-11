package db.impl.model.common

/**
  * Represents a model with a description.
  */
trait Describable {

  /**
    * Returns the models's description.
    *
    * @return Model description
    */
  def description: Option[String]

}
