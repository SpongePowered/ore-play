package ore.db.impl.schema

import java.time.Instant

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.api.{ApiKey, ApiSession}
import ore.models.user.User

class ApiKeySessionTable(tag: Tag) extends ModelTable[ApiSession](tag, "api_sessions") {
  def token   = column[String]("token")
  def keyId   = column[Option[DbRef[ApiKey]]]("key_id")
  def userId  = column[Option[DbRef[User]]]("user_id")
  def expires = column[Instant]("expires")

  override def * =
    (id.?, createdAt.?, (token, keyId, userId, expires)) <> (mkApply((ApiSession.apply _).tupled), mkUnapply(
      ApiSession.unapply
    ))
}
