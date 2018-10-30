package models.project

import db.impl.schema.UnsafeDownloadsTable
import db.{DbRef, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.user.User
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
    id: ObjId[UnsafeDownload] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: Option[DbRef[User]] = None,
    address: InetString,
    downloadType: DownloadType
) extends Model {

  override type M = UnsafeDownload
  override type T = UnsafeDownloadsTable
}
object UnsafeDownload {
  implicit val query: ModelQuery[UnsafeDownload] =
    ModelQuery.from[UnsafeDownload](TableQuery[UnsafeDownloadsTable], _.copy(_, _))
}
