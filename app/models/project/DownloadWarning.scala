package models.project

import java.sql.Timestamp

import play.api.mvc.Cookie

import controllers.sugar.Bakery
import db.impl.schema.DownloadWarningsTable
import db.{Expirable, Model, ObjectId, ObjectReference, ObjectTimestamp}
import models.project.DownloadWarning.COOKIE

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._

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
  */
case class DownloadWarning(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    expiration: Timestamp,
    token: String,
    versionId: ObjectReference,
    address: InetString,
    isConfirmed: Boolean = false,
) extends Model
    with Expirable {

  override type M = DownloadWarning
  override type T = DownloadWarningsTable

  /**
    * Creates a cookie that should be given to the client.
    *
    * @return Cookie for client
    */
  def cookie(implicit bakery: Bakery): Cookie = {
    checkNotNull(this.token, "null token", "")
    bakery.bake(COOKIE + "_" + this.versionId, this.token)
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): DownloadWarning =
    this.copy(id = id, createdAt = theTime)
}

object DownloadWarning {

  /**
    * Cookie identifier name.
    */
  val COOKIE = "_warning"

}
