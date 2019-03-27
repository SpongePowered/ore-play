package models.api

import db.impl.schema.ApiKeyTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}
import models.user.User
import ore.permission.Permission
import ore.user.UserOwned

import slick.lifted.TableQuery

case class ApiKey(
    ownerId: DbRef[User],
    token: String,
    permissions: Permission
)
object ApiKey extends DefaultModelCompanion[ApiKey, ApiKeyTable](TableQuery[ApiKeyTable]) {
  implicit val query: ModelQuery[ApiKey] = ModelQuery.from(this)

  implicit val isUserOwned: UserOwned[ApiKey] = (a: ApiKey) => a.ownerId
}
