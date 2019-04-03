package models.api

import java.time.Instant

import db.impl.schema.ApiKeySessionTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}
import models.user.User

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
