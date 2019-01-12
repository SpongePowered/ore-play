package models.user

import db.impl.schema.SignOnTable
import db.{InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}

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
    id: ObjId[SignOn],
    createdAt: ObjectTimestamp,
    nonce: String,
    isCompleted: Boolean
) extends Model {

  override type M = SignOn
  override type T = SignOnTable
}
object SignOn {
  def partial(nonce: String, isCompleted: Boolean = false): InsertFunc[SignOn] =
    (id, time) => SignOn(id, time, nonce, isCompleted)

  implicit val query: ModelQuery[SignOn] =
    ModelQuery.from[SignOn](TableQuery[SignOnTable], _.copy(_, _))
}
