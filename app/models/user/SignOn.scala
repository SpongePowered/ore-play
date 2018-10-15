package models.user

import db.impl.schema.SignOnTable
import db.{Model, ModelQuery, ObjectId, ObjectTimestamp}

import slick.lifted.TableQuery

/**
  * Represents a sign-on instance for a user.
  *
  * @param id           User ID
  * @param createdAt    Instant of creation
  * @param nonce        Nonce used
  * @param isCompleted  True if sign on was completed
  */
case class SignOn(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    nonce: String,
    isCompleted: Boolean = false
) extends Model {

  override type M = SignOn
  override type T = SignOnTable

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): SignOn = this.copy(id = id, createdAt = theTime)
}
object SignOn {
  implicit val query: ModelQuery[SignOn] =
    ModelQuery.from[SignOn](TableQuery[SignOnTable])
}
