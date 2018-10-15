package models.project

import db.impl.schema.UnsafeDownloadsTable
import db.{Model, ModelQuery, ObjectId, ObjectReference, ObjectTimestamp}
import ore.project.io.DownloadType

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents a download instance of an unreviewed [[Project]] [[Version]].
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param userId       User who downloaded (if applicable)
  * @param address      Address of client
  * @param downloadType Type of download
  */
case class UnsafeDownload(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: Option[ObjectReference] = None,
    address: InetString,
    downloadType: DownloadType
) extends Model {

  override type M = UnsafeDownload
  override type T = UnsafeDownloadsTable

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): UnsafeDownload = this.copy(id = id, createdAt = theTime)
}
object UnsafeDownload {
  implicit val query: ModelQuery[UnsafeDownload] =
    ModelQuery.from[UnsafeDownload](TableQuery[UnsafeDownloadsTable])
}
