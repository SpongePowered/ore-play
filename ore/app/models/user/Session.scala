package models.user

import java.sql.Timestamp

import db.impl.DefaultModelCompanion
import db.impl.access.UserBase
import db.impl.common.Expirable
import db.impl.schema.SessionTable
import ore.db.{Model, ModelQuery}
import security.spauth.SpongeAuthApi
import util.OreMDC

import cats.data.OptionT
import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a persistant [[User]] session.
  *
  * @param expiration Instant of expiration
  * @param username   Username session belongs to
  * @param token      Unique token
  */
case class Session(
    expiration: Timestamp,
    username: String,
    token: String
) extends Expirable {

  /**
    * Returns the [[User]] that this Session belongs to.
    *
    * @param users UserBase instance
    * @return User session belongs to
    */
  def user(implicit users: UserBase, auth: SpongeAuthApi, mdc: OreMDC): OptionT[IO, Model[User]] =
    users.withName(this.username)
}
object Session extends DefaultModelCompanion[Session, SessionTable](TableQuery[SessionTable]) {

  implicit val query: ModelQuery[Session] =
    ModelQuery.from(this)
}
