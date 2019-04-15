package db.impl.schema

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import models.project.{DownloadWarning, UnsafeDownload, Version}
import ore.db.DbRef

import com.github.tminglei.slickpg.InetString

class DownloadWarningsTable(tag: Tag) extends ModelTable[DownloadWarning](tag, "project_version_download_warnings") {

  def expiration  = column[Timestamp]("expiration")
  def token       = column[String]("token")
  def versionId   = column[DbRef[Version]]("version_id")
  def address     = column[InetString]("address")
  def downloadId  = column[DbRef[UnsafeDownload]]("download_id")
  def isConfirmed = column[Boolean]("is_confirmed")

  override def * =
    (id.?, createdAt.?, (expiration, token, versionId, address, isConfirmed, downloadId.?)) <> (mkApply(
      (DownloadWarning.apply _).tupled
    ), mkUnapply(DownloadWarning.unapply))
}
