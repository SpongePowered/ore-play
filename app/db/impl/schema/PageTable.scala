package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import models.project.{Page, Project}

class PageTable(tag: Tag) extends ModelTable[Page](tag, "project_pages") with NameColumn[Page] {

  def projectId   = column[DbRef[Project]]("project_id")
  def parentId    = column[Option[DbRef[Page]]]("parent_id")
  def slug        = column[String]("slug")
  def contents    = column[String]("contents")
  def isDeletable = column[Boolean]("is_deletable")

  override def * = mkProj((id.?, createdAt.?, projectId, parentId, name, slug, isDeletable, contents))(mkTuple[Page]())
}
