package models.project

import java.sql.Timestamp

import play.api.mvc.Cookie

import controllers.sugar.Bakery
import db.impl.model.common.Expirable
import db.impl.schema.DownloadWarningsTable
import db.{DbRef, DefaultDbModelCompanion, ModelQuery}
import models.project.DownloadWarning.COOKIE

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
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
) extends Expirable {

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

object DownloadWarning
    extends DefaultDbModelCompanion[DownloadWarning, DownloadWarningsTable](TableQuery[DownloadWarningsTable]) {

  implicit val query: ModelQuery[DownloadWarning] =
    ModelQuery.from(this)

  /**
    * Cookie identifier name.
    */
  val COOKIE = "_warning"

}
