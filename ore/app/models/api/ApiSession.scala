package models.api

import java.time.Instant

import db.impl.DefaultModelCompanion
import db.impl.schema.ApiKeySessionTable
import models.user.User
import ore.db.{DbRef, ModelQuery}

import slick.lifted.TableQuery

case class ApiSession(
    token: String,
    keyId: Option[DbRef[ApiKey]],
    userId: Option[DbRef[User]],
    expires: Instant
)
object ApiSession extends DefaultModelCompanion[ApiSession, ApiKeySessionTable](TableQuery[ApiKeySessionTable]) {
  implicit val query: ModelQuery[ApiSession] = ModelQuery.from(this)
}
