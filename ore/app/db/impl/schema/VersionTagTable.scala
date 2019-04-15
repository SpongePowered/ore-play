package db.impl.schema

import java.sql.Timestamp
import java.time.Instant

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import models.project.{TagColor, Version, VersionTag}
import ore.db.{DbRef, Model, ObjId, ObjTimestamp}

class VersionTagTable(tag: Tag)
    extends ModelTable[VersionTag](tag, "project_version_tags")
    with NameColumn[VersionTag] {

  def versionId = column[DbRef[Version]]("version_id")
  def data      = column[String]("data")
  def color     = column[TagColor]("color")

  override def * = {
    val convertedApply: ((Option[DbRef[VersionTag]], DbRef[Version], String, String, TagColor)) => Model[VersionTag] = {
      case (id, versionIds, name, data, color) =>
        Model(
          ObjId.unsafeFromOption(id),
          ObjTimestamp(Timestamp.from(Instant.EPOCH)),
          VersionTag(versionIds, name, data, color)
        )
    }
    val convertedUnapply
      : PartialFunction[Model[VersionTag], (Option[DbRef[VersionTag]], DbRef[Version], String, String, TagColor)] = {
      case Model(id, _, VersionTag(versionIds, name, data, color)) =>
        (id.unsafeToOption, versionIds, name, data, color)
    }
    (id.?, versionId, name, data, color) <> (convertedApply, convertedUnapply.lift)
  }
}
