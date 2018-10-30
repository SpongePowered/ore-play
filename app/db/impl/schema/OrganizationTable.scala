package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import models.user.{Organization, User}

class OrganizationTable(tag: RowTag)
    extends ModelTable[Organization](tag, "organizations")
    with NameColumn[Organization] {

  override def id = column[DbRef[Organization]]("id", O.PrimaryKey)
  def userId      = column[DbRef[User]]("user_id")

  override def * = mkProj((id.?, createdAt.?, name, userId))(mkTuple[Organization]())
}
