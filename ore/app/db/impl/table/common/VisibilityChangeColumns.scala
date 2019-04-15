package db.impl.table.common

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import db.impl.common.VisibilityChange
import models.project.Visibility
import models.user.User
import ore.db.DbRef

trait VisibilityChangeColumns[M <: VisibilityChange] extends ModelTable[M] {

  def createdBy  = column[DbRef[User]]("created_by")
  def comment    = column[String]("comment")
  def resolvedAt = column[Timestamp]("resolved_at")
  def resolvedBy = column[DbRef[User]]("resolved_by")
  def visibility = column[Visibility]("visibility")
}
