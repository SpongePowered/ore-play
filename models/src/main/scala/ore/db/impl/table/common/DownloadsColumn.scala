package ore.db.impl.table.common

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.Downloadable

/**
  * Represents a [[ModelTable]] column for keeping track of downloads.
  *
  * @tparam M Model type
  */
trait DownloadsColumn[M <: Downloadable] extends ModelTable[M] {

  /**
    * The column that keeps track of downloads.
    *
    * @return Column that keeps track of downloads
    */
  def downloads = column[Long]("downloads")

}
