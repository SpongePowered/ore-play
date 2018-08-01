package models.project

import com.github.tminglei.slickpg.InetString
import db.{Model, ObjectId, ObjectTimestamp}
import db.impl.UnsafeDownloadsTable
import ore.project.io.DownloadTypes.DownloadType

/**
  * Represents a download instance of an unreviewed [[Project]] [[Version]].
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param userId       User who downloaded (if applicable)
  * @param address      Address of client
  * @param downloadType Type of download
  */
case class UnsafeDownload(id: ObjectId = ObjectId.Uninitialized,
                          createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                          userId: Option[Int] = None,
                          address: InetString,
                          downloadType: DownloadType) extends Model {

  override type M = UnsafeDownload
  override type T = UnsafeDownloadsTable

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): UnsafeDownload = this.copy(id = id, createdAt = theTime)
}
