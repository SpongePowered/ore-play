package db.impl.model.common

import db.Model
import db.impl.table.common.DownloadsColumn

/**
  * Represents a [[Model]] that keeps track of downloads.
  */
trait Downloadable extends Model { self =>

  override type M <: Downloadable { type M = self.M }
  override type T <: DownloadsColumn[M]

  /**
    * The amount of downloads the [[Model]] has.
    *
    * @return Downloads model has
    */
  def downloadCount: Long

}
