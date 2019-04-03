package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.api.ApiKey
import models.user.User
import ore.permission.Permission

class ApiKeyTable(tag: Tag) extends ModelTable[ApiKey](tag, "api_keys") {
  def ownerId           = column[DbRef[User]]("owner_id")
  def token             = column[String]("token")
  def rawKeyPermissions = column[Permission]("raw_key_permissions")

  override def * =
    (id.?, createdAt.?, (ownerId, token, rawKeyPermissions)) <> (mkApply((ApiKey.apply _).tupled), mkUnapply(
      ApiKey.unapply
    ))
}
