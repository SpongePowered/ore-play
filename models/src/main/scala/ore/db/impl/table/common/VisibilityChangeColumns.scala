package ore.db.impl.table.common

import java.time.Instant

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.VisibilityChange
import ore.models.project.Visibility
import ore.models.user.User
import ore.db.DbRef

trait VisibilityChangeColumns[M <: VisibilityChange] extends ModelTable[M] {

  def createdBy  = column[DbRef[User]]("created_by")
  def comment    = column[String]("comment")
  def resolvedAt = column[Instant]("resolved_at")
  def resolvedBy = column[DbRef[User]]("resolved_by")
  def visibility = column[Visibility]("visibility")
}
