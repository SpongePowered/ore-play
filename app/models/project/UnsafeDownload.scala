package models.project

import com.github.tminglei.slickpg.InetString
import db.{ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.UnsafeDownloadsTable
import db.impl.model.OreModel
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
case class UnsafeDownload(override val id: ObjectId = ObjectId.Uninitialized,
                          override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                          userId: Option[ObjectReference] = None,
                          address: InetString,
                          downloadType: DownloadType) extends OreModel(id, createdAt) {

  override type M = UnsafeDownload
  override type T = UnsafeDownloadsTable

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): UnsafeDownload = this.copy(id = id, createdAt = theTime)
}
