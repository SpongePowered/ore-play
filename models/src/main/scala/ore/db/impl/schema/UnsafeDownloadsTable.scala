package ore.db.impl.schema

import ore.data.DownloadType
import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.UnsafeDownload
import ore.models.user.User

import com.github.tminglei.slickpg.InetString

class UnsafeDownloadsTable(tag: Tag) extends ModelTable[UnsafeDownload](tag, "project_version_unsafe_downloads") {

  def userId       = column[DbRef[User]]("user_id")
  def address      = column[InetString]("address")
  def downloadType = column[DownloadType]("download_type")

  override def * =
    (id.?, createdAt.?, (userId.?, address, downloadType)).<>(
      mkApply((UnsafeDownload.apply _).tupled),
      mkUnapply(
        UnsafeDownload.unapply
      )
    )
}
