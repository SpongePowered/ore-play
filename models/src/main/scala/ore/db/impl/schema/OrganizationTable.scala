package ore.db.impl.schema

import java.time.Instant

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.NameColumn
import ore.db.{DbRef, Model, ObjId, ObjInstant}
import ore.models.organization.Organization
import ore.models.user.User

class OrganizationTable(tag: Tag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id = column[DbRef[Organization]]("id", O.PrimaryKey)
  def userId      = column[DbRef[User]]("user_id")

  override def * = {
    val applyFunc: ((Option[DbRef[Organization]], Option[Instant], String, DbRef[User])) => Model[Organization] = {
      case (id, time, name, userId) =>
        Model(
          ObjId.unsafeFromOption(id),
          ObjInstant.unsafeFromOption(time),
          Organization(ObjId.unsafeFromOption(id), name, userId)
        )
    }

    val unapplyFunc
      : Model[Organization] => Option[(Option[DbRef[Organization]], Option[Instant], String, DbRef[User])] = {
      case Model(_, createdAt, Organization(id, username, ownerId)) =>
        Some((id.unsafeToOption, createdAt.unsafeToOption, username, ownerId))
    }

    (id.?, createdAt.?, name, userId) <> (applyFunc, unapplyFunc)
  }
}
