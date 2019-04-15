package db.impl.model.common

import java.sql.Timestamp

import db.DbRef
import models.project.Visibility
import models.user.User

trait VisibilityChange {

  def createdBy: Option[DbRef[User]]
  def comment: String
  def resolvedAt: Option[Timestamp]
  def resolvedBy: Option[DbRef[User]]
  def visibility: Visibility

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
