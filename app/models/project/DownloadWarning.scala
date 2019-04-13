package models.project

import java.sql.Timestamp

import db.impl.model.common.Expirable
import db.impl.schema.DownloadWarningsTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents an instance of a warning that a client has landed on. Warnings
  * will expire and are associated with a certain inet address.
  *
  * @param expiration   Instant of expiration
  * @param token        Unique token for the client to identify by
  * @param versionId    Version ID the warning is for
  * @param address      Address of client who landed on the warning
  * @param downloadId  Download ID
  */
case class DownloadWarning(
    expiration: Timestamp,
    token: String,
    versionId: DbRef[Version],
    address: InetString,
    isConfirmed: Boolean = false,
    downloadId: Option[DbRef[UnsafeDownload]]
) extends Expirable

object DownloadWarning
    extends DefaultModelCompanion[DownloadWarning, DownloadWarningsTable](TableQuery[DownloadWarningsTable]) {

  implicit val query: ModelQuery[DownloadWarning] =
    ModelQuery.from(this)

  def cookieKey(versionId: DbRef[Version]) = s"_warning_$versionId"

}
