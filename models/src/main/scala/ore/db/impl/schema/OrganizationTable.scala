package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.NameColumn
import ore.db.{DbRef, Model, ObjId, ObjOffsetDateTime}
import ore.models.organization.Organization
import ore.models.user.User

class OrganizationTable(tag: Tag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id = column[DbRef[Organization]]("id", O.PrimaryKey)
  def ownerId     = column[DbRef[User]]("owner_id")
  def userId      = column[DbRef[User]]("user_id")

  override def * = {
    val applyFunc: ((Option[DbRef[Organization]], Option[OffsetDateTime], DbRef[User], String, DbRef[User])) => Model[
      Organization
    ] = {
      case (id, time, userId, name, ownerId) =>
        Model(
          ObjId.unsafeFromOption(id),
          ObjOffsetDateTime.unsafeFromOption(time),
          Organization(ObjId.unsafeFromOption(id), userId, name, ownerId)
        )
    }

    val unapplyFunc: Model[Organization] => Option[
      (Option[DbRef[Organization]], Option[OffsetDateTime], DbRef[User], String, DbRef[User])
    ] = {
      case Model(_, createdAt, Organization(id, userId, name, ownerId)) =>
        Some((id.unsafeToOption, createdAt.unsafeToOption, userId, name, ownerId))
    }

    (id.?, createdAt.?, userId, name, ownerId).<>(applyFunc, unapplyFunc)
  }
}
