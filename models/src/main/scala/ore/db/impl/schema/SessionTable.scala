package ore.db.impl.schema

import java.time.Instant

import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.{Session => DbSession}

class SessionTable(tag: Tag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[Instant]("expiration")
  def username   = column[String]("username")
  def token      = column[String]("token")

  def * =
    (id.?, createdAt.?, (expiration, username, token)) <> (mkApply((DbSession.apply _).tupled), mkUnapply(
      DbSession.unapply
    ))
}
