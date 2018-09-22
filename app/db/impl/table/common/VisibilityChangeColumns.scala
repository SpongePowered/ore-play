package db.impl.table.common

import java.sql.Timestamp

import db.ObjectReference
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.VisibilityChange
import db.table.ModelTable
import models.project.Visibility

trait VisibilityChangeColumns[M <: VisibilityChange] extends ModelTable[M] {

  def createdBy  = column[ObjectReference]("created_by")
  def comment    = column[String]("comment")
  def resolvedAt = column[Timestamp]("resolved_at")
  def resolvedBy = column[ObjectReference]("resolved_by")
  def visibility = column[Visibility]("visibility")
}
