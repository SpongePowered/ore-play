package db.impl.schema

import java.time.Instant

import db.impl.OrePostgresDriver.api._
import models.api.{ApiKey, ApiSession}
import models.user.User
import ore.db.DbRef

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
