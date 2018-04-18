package db.impl.table.common

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import db.impl.model.common.VisibilityChange
import db.table.ModelTable

trait VisibilityChangeColumns[M <: VisibilityChange] extends ModelTable[M] {

  def createdBy= column[Int]("created_by")
  def comment= column[String]("comment")
  def resolvedAt= column[Timestamp]("resolved_at")
  def resolvedBy= column[Int]("resolved_by")
  def visibility= column[Int]("visibility")
}
