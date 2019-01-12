package db.impl.model.common

import java.sql.Timestamp

import db.impl.access.UserBase
import db.impl.table.common.VisibilityChangeColumns
import db.{DbRef, Model}
import models.project.Visibility
import models.user.User

import cats.data.OptionT
import cats.effect.IO

trait VisibilityChange extends Model { self =>

  type M <: VisibilityChange { type M = self.M }
  type T <: VisibilityChangeColumns[M]

  def createdBy: Option[DbRef[User]]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[DbRef[User]]
  def visibility: Visibility

  def created(implicit userBase: UserBase): OptionT[IO, User] =
    OptionT.fromOption[IO](createdBy).flatMap(userBase.get(_))

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
