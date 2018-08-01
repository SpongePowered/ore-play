package db.impl.model.common

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import db.Model
import db.impl.access.UserBase
import db.impl.table.common.VisibilityChangeColumns
import models.user.User
import util.functional.OptionT
import util.instances.future._

trait VisibilityChange extends Model { self =>

  type M <: VisibilityChange { type M = self.M }
  type T <: VisibilityChangeColumns[M]

  def createdBy: Option[Int]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[Int]
  def visibility: Int

  def created(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] =
    OptionT.fromOption[Future](createdBy).flatMap(userBase.get(_))

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
