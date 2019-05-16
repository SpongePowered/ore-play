package ore.models.user

import java.time.Instant

import ore.db.ModelQuery
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.Expirable
import ore.db.impl.schema.SessionTable

import slick.lifted.TableQuery

/**
  * Represents a persistant [[User]] session.
  *
  * @param expiration Instant of expiration
  * @param username   Username session belongs to
  * @param token      Unique token
  */
case class Session(
    expiration: Instant,
    username: String,
    token: String
) extends Expirable
object Session extends DefaultModelCompanion[Session, SessionTable](TableQuery[SessionTable]) {

  implicit val query: ModelQuery[Session] =
    ModelQuery.from(this)
}
