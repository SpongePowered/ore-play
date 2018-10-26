package models.project

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.Cookie

import controllers.sugar.Bakery
import db.impl.model.common.Expirable
import db.impl.schema.DownloadWarningsTable
import db.{Model, ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import models.project.DownloadWarning.COOKIE

import cats.data.OptionT
import cats.instances.future._
import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
import slick.lifted.TableQuery

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
case class DownloadWarning(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    expiration: Timestamp,
    token: String,
    versionId: ObjectReference,
    address: InetString,
    isConfirmed: Boolean = false,
    downloadId: Option[ObjectReference]
) extends Model
    with Expirable {

  override type M = DownloadWarning
  override type T = DownloadWarningsTable

  /**
    * Returns the download this warning was for.
    *
    * @return Download
    */
  def download(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, UnsafeDownload] =
    OptionT.fromOption[Future](downloadId).flatMap(service.access[UnsafeDownload]().get)

  /**
    * Creates a cookie that should be given to the client.
    *
    * @return Cookie for client
    */
  def cookie(implicit bakery: Bakery): Cookie = {
    checkNotNull(this.token, "null token", "")
    bakery.bake(COOKIE + "_" + this.versionId, this.token)
  }
}

object DownloadWarning {

  implicit val query: ModelQuery[DownloadWarning] =
    ModelQuery.from[DownloadWarning](TableQuery[DownloadWarningsTable], _.copy(_, _))

  /**
    * Cookie identifier name.
    */
  val COOKIE = "_warning"

}
