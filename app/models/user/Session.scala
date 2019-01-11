package models.user

import java.sql.Timestamp

import db.impl.access.UserBase
import db.impl.model.common.Expirable
import db.impl.schema.SessionTable
import db.{InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import security.spauth.SpongeAuthApi
import util.OreMDCCtx

import cats.data.OptionT
import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a persistant [[User]] session.
  *
  * @param id         Unique ID
  * @param createdAt  Instant of creation
  * @param expiration Instant of expiration
  * @param username   Username session belongs to
  * @param token      Unique token
  */
case class Session(
    id: ObjId[Session],
    createdAt: ObjectTimestamp,
    expiration: Timestamp,
    username: String,
    token: String
) extends Model
    with Expirable {

  override type M = Session
  override type T = SessionTable

  /**
    * Returns the [[User]] that this Session belongs to.
    *
    * @param users UserBase instance
    * @return User session belongs to
    */
  def user(implicit users: UserBase, auth: SpongeAuthApi, mdc: OreMDCCtx): OptionT[IO, User] =
    users.withName(this.username)
}
object Session {

  def partial(
      expiration: Timestamp,
      username: String,
      token: String
  ): InsertFunc[Session] = (id, time) => Session(id, time, expiration, username, token)

  implicit val query: ModelQuery[Session] =
    ModelQuery.from[Session](TableQuery[SessionTable], _.copy(_, _))
}
