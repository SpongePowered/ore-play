package models.project

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._

import controllers.sugar.Bakery
import db.{Expirable, ObjectId, ObjectReference, ObjectTimestamp, Model, ModelService}
import db.impl.DownloadWarningsTable
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
  * @param downloadId  Download ID
  */
case class DownloadWarning(id: ObjectId = ObjectId.Uninitialized,
                           createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                           expiration: Timestamp,
                           token: String,
                           versionId: ObjectReference,
                           address: InetString,
                           isConfirmed: Boolean = false,
                           downloadId: ObjectReference = -1) extends Model with Expirable {

  override type M = DownloadWarning
  override type T = DownloadWarningsTable

  /**
    * Returns the download this warning was for.
    *
    * @return Download
    */
  def download(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, UnsafeDownload] = {
    if (downloadId == -1)
      OptionT.none[Future, UnsafeDownload]
    else
      service.access[UnsafeDownload](classOf[UnsafeDownload]).get(downloadId)
  }

  /**
    * Creates a cookie that should be given to the client.
    *
    * @return Cookie for client
    */
  def cookie(implicit bakery: Bakery): Cookie = {
    checkNotNull(this.token, "null token", "")
    bakery.bake(COOKIE + "_" + this.versionId, this.token)
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): DownloadWarning = this.copy(id = id, createdAt = theTime)
}

object DownloadWarning {

  /**
    * Cookie identifier name.
    */
  val COOKIE = "_warning"

}
