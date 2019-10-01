package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.{User, Session => DbSession}

class SessionTable(tag: Tag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[OffsetDateTime]("expiration")
  def userId     = column[DbRef[User]]("user_id")
  def token      = column[String]("token")

  def * =
    (id.?, createdAt.?, (expiration, userId, token)) <> (mkApply((DbSession.apply _).tupled), mkUnapply(
      DbSession.unapply
    ))
}
