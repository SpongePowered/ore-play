package models.user

import java.sql.Timestamp

import scala.concurrent.Future

import db.{ObjectId, ObjectTimestamp}
import db.impl.SignOnTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys._

/**
  * Represents a sign-on instance for a user.
  *
  * @param id           User ID
  * @param createdAt    Instant of creation
  * @param nonce        Nonce used
  * @param _isCompleted True if sign on was completed
  */
case class SignOn(override val id: ObjectId = ObjectId.Uninitialized,
                  override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                  nonce: String,
                  var _isCompleted: Boolean = false) extends OreModel(id, createdAt) {

  override type M = SignOn
  override type T = SignOnTable

  def isCompleted: Boolean = this._isCompleted

  def setCompleted(completed: Boolean = true): Future[Int] = Defined {
    this._isCompleted = completed
    update(IsCompleted)
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): SignOn = this.copy(id = id, createdAt = theTime)
}
