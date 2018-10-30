package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import db.{DbRef, ObjId}
import models.project.{Tag, TagColor, Version}

class TagTable(tag: RowTag) extends ModelTable[ProjectTag](tag, "project_tags") with NameColumn[ProjectTag] {

  def versionIds = column[List[DbRef[Version]]]("version_ids")
  def data       = column[String]("data")
  def color      = column[TagColor]("color")

  override def * = {
    val convertedApply: ((Option[DbRef[ProjectTag]], List[DbRef[Version]], String, String, TagColor)) => ProjectTag = {
      case (id, versionIds, name, data, color) => Tag(ObjId.unsafeFromOption(id), versionIds, name, data, color)
    }
    val convertedUnapply
      : PartialFunction[ProjectTag, (Option[DbRef[ProjectTag]], List[DbRef[Version]], String, String, TagColor)] = {
      case Tag(id, versionIds, name, data, color) => (id.unsafeToOption, versionIds, name, data, color)
    }
    (id.?, versionIds, name, data, color) <> (convertedApply, convertedUnapply.lift)
  }
}
