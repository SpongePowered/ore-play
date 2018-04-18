package db.impl.model.common

import java.sql.Timestamp

import scala.concurrent.Future

import db.Model
import db.impl.table.common.VisibilityChangeColumns
import models.user.User

trait VisibilityChange extends Model { self =>

  override type M <: VisibilityChange { type M = self.M }
  override type T <: VisibilityChangeColumns[M]

  def createdBy: Option[Int]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[Int]
  def visibility: Int

  def created: Future[Option[User]]

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined

}
