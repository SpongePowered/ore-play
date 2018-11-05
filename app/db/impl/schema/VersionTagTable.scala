package db.impl.schema

import db.{DbRef, ObjId}
import db.impl.table.common.NameColumn
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.{TagColor, Version, VersionTag}

class VersionTagTable(tag: Tag)
    extends ModelTable[VersionTag](tag, "project_version_tags")
    with NameColumn[VersionTag] {

  def versionId = column[DbRef[Version]]("version_id")
  def data      = column[String]("data")
  def color     = column[TagColor]("color")

  override def * = {
    val convertedApply: ((Option[DbRef[VersionTag]], DbRef[Version], String, String, TagColor)) => VersionTag = {
      case (id, versionIds, name, data, color) =>
        VersionTag(ObjId.unsafeFromOption(id), versionIds, name, data, color)
    }
    val convertedUnapply
      : PartialFunction[VersionTag, (Option[DbRef[VersionTag]], DbRef[Version], String, String, TagColor)] = {
      case VersionTag(id, versionIds, name, data, color) => (id.unsafeToOption, versionIds, name, data, color)
    }
    (id.?, versionId, name, data, color) <> (convertedApply, convertedUnapply.lift)
  }
}
