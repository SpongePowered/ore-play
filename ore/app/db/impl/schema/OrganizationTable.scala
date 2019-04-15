package db.impl.schema

import java.sql.Timestamp

import db.{Model, DbRef, ObjId, ObjTimestamp}
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import models.user.{Organization, User}

class OrganizationTable(tag: Tag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id = column[DbRef[Organization]]("id", O.PrimaryKey)
  def userId      = column[DbRef[User]]("user_id")

  override def * = {
    val applyFunc: ((Option[DbRef[Organization]], Option[Timestamp], String, DbRef[User])) => Model[Organization] = {
      case (id, time, name, userId) =>
        Model(
          ObjId.unsafeFromOption(id),
          ObjTimestamp.unsafeFromOption(time),
          Organization(ObjId.unsafeFromOption(id), name, userId)
        )
    }

    val unapplyFunc
      : Model[Organization] => Option[(Option[DbRef[Organization]], Option[Timestamp], String, DbRef[User])] = {
      case Model(_, createdAt, Organization(id, username, ownerId)) =>
        Some((id.unsafeToOption, createdAt.unsafeToOption, username, ownerId))
    }

    (id.?, createdAt.?, name, userId) <> (applyFunc, unapplyFunc)
  }
}
