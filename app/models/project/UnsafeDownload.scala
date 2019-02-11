package models.project

import db.impl.schema.UnsafeDownloadsTable
import db.{DbRef, DefaultDbModelCompanion, ModelQuery}
import models.user.User
import ore.project.io.DownloadType

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents a download instance of an unreviewed [[Project]] [[Version]].
  *
  * @param userId       User who downloaded (if applicable)
  * @param address      Address of client
  * @param downloadType Type of download
  */
case class UnsafeDownload(
    userId: Option[DbRef[User]],
    address: InetString,
    downloadType: DownloadType
)
object UnsafeDownload
    extends DefaultDbModelCompanion[UnsafeDownload, UnsafeDownloadsTable](TableQuery[UnsafeDownloadsTable]) {

  implicit val query: ModelQuery[UnsafeDownload] = ModelQuery.from(this)
}
