package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{DownloadWarning, UnsafeDownload, Version}

import com.github.tminglei.slickpg.InetString

class DownloadWarningsTable(tag: Tag) extends ModelTable[DownloadWarning](tag, "project_version_download_warnings") {

  def expiration  = column[OffsetDateTime]("expiration")
  def token       = column[String]("token")
  def versionId   = column[DbRef[Version]]("version_id")
  def address     = column[InetString]("address")
  def downloadId  = column[DbRef[UnsafeDownload]]("download_id")
  def isConfirmed = column[Boolean]("is_confirmed")

  override def * =
    (id.?, createdAt.?, (expiration, token, versionId, address, isConfirmed, downloadId.?)).<>(
      mkApply(
        (DownloadWarning.apply _).tupled
      ),
      mkUnapply(DownloadWarning.unapply)
    )
}
