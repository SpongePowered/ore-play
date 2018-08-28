package db.impl.model.common

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import db.Model
import db.impl.table.common.VisibilityChangeColumns
import models.user.User
import util.functional.OptionT

trait VisibilityChange extends Model { self =>

  type M <: VisibilityChange { type M = self.M }
  type T <: VisibilityChangeColumns[M]

  def createdBy: Option[Int]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[Int]
  def visibility: Int

  def created(implicit ec: ExecutionContext): OptionT[Future, User]

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
