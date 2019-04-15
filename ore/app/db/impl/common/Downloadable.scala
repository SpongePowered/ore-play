package db.impl.common

/**
  * Represents a model that keeps track of downloads.
  */
trait Downloadable {

  /**
    * The amount of downloads the model has.
    *
    * @return Downloads model has
    */
  def downloadCount: Long

}
