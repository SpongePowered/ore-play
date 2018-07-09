package models.project

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
import controllers.sugar.Bakery
import db.{Expirable, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.DownloadWarningsTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import models.project.DownloadWarning.COOKIE
import util.functional.OptionT
import util.instances.future._
import play.api.mvc.Cookie

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents an instance of a warning that a client has landed on. Warnings
  * will expire and are associated with a certain inet address.
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param expiration   Instant of expiration
  * @param token        Unique token for the client to identify by
  * @param versionId    Version ID the warning is for
  * @param address      Address of client who landed on the warning
  * @param _downloadId  Download ID
  */
case class DownloadWarning(override val id: ObjectId = ObjectId.Uninitialized,
                           override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                           override val expiration: Timestamp,
                           token: String,
                           versionId: ObjectReference,
                           address: InetString,
                           private var _isConfirmed: Boolean = false,
                           private var _downloadId: ObjectReference = -1) extends OreModel(id, createdAt) with Expirable {

  override type M = DownloadWarning
  override type T = DownloadWarningsTable

  def isConfirmed: Boolean = this._isConfirmed

  def setConfirmed(confirmed: Boolean = true): Future[Int] = Defined {
    this._isConfirmed = confirmed
    update(IsConfirmed)
  }

  /**
    * Returns the ID of the download this warning was for.
    *
    * @return Download ID
    */
  def downloadId: ObjectReference = this._downloadId

  /**
    * Returns the download this warning was for.
    *
    * @return Download
    */
  def download(implicit ec: ExecutionContext): OptionT[Future, UnsafeDownload] = {
    if (this._downloadId == -1)
      OptionT.none[Future, UnsafeDownload]
    else
      this.service.access[UnsafeDownload](classOf[UnsafeDownload]).get(this._downloadId)
  }

  /**
    * Sets the download this warning was for.
    *
    * @param download Download warning was for
    */
  def setDownload(download: UnsafeDownload): Future[Int] = Defined {
    checkNotNull(download, "null download", "")
    checkArgument(download.isDefined, "undefined download", "")
    this._downloadId = download.id.value
    update(DownloadId)
  }

  /**
    * Creates a cookie that should be given to the client.
    *
    * @return Cookie for client
    */
  def cookie(implicit bakery: Bakery): Cookie = {
    checkNotNull(this.token, "null token", "")
    bakery.bake(COOKIE, this.token)
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): DownloadWarning = this.copy(id = id, createdAt = theTime)
}

object DownloadWarning {

  /**
    * Cookie identifier name.
    */
  val COOKIE = "_warning"

}
