package db.impl.schema

import java.sql.Timestamp
import java.time.Instant

import db.{DbModel, DbRef, ObjId, ObjTimestamp}
import db.table.ModelTable
import db.impl.OrePostgresDriver.api._
import models.user.role.DbRole
import ore.permission.role.{RoleCategory, Trust}

class DbRoleTable(tag: Tag) extends ModelTable[DbRole](tag, "roles") {
  def name         = column[String]("name")
  def category     = column[RoleCategory]("category")
  def trust        = column[Trust]("trust")
  def title        = column[String]("title")
  def color        = column[String]("color")
  def isAssignable = column[Boolean]("is_assignable")
  def rank         = column[Int]("rank")

  override def * = {
    val applyFunc: (
        (Option[DbRef[DbRole]], String, RoleCategory, Trust, String, String, Boolean, Option[Int])
    ) => DbModel[DbRole] = {
      case (id, name, category, trust, title, color, isAssignable, rank) =>
        DbModel(
          ObjId.unsafeFromOption(id),
          ObjTimestamp(Timestamp.from(Instant.EPOCH)),
          DbRole(name, category, trust, title, color, isAssignable, rank)
        )
    }

    val unapplyFunc: DbModel[DbRole] => Option[
      (Option[DbRef[DbRole]], String, RoleCategory, Trust, String, String, Boolean, Option[Int])
    ] = {
      case DbModel(id, _, DbRole(name, category, trust, title, color, isAssignable, rank)) =>
        Some((id.unsafeToOption, name, category, trust, title, color, isAssignable, rank))
    }

    (id.?, name, category, trust, title, color, isAssignable, rank.?) <> (applyFunc, unapplyFunc)
  }
}
