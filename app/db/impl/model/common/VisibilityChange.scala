package db.impl.model.common

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.impl.table.common.VisibilityChangeColumns
import db.{Model, ObjectReference}
import models.project.Visibility
import models.user.User

import cats.data.OptionT
import cats.instances.future._

trait VisibilityChange extends Model { self =>

  type M <: VisibilityChange { type M = self.M }
  type T <: VisibilityChangeColumns[M]

  def createdBy: Option[ObjectReference]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[ObjectReference]
  def visibility: Visibility

  def created(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] =
    OptionT.fromOption[Future](createdBy).flatMap(userBase.get(_))

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
