package ore.models.project

import ore.data.DownloadType
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.UnsafeDownloadsTable
import ore.db.{DbRef, ModelQuery}
import ore.models.user.User

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
    extends DefaultModelCompanion[UnsafeDownload, UnsafeDownloadsTable](TableQuery[UnsafeDownloadsTable]) {

  implicit val query: ModelQuery[UnsafeDownload] = ModelQuery.from(this)
}
