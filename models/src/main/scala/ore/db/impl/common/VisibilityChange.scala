package ore.db.impl.common

import java.time.Instant

import ore.db.DbRef
import ore.models.project.Visibility
import ore.models.user.User

trait VisibilityChange {

  def createdBy: Option[DbRef[User]]
  def comment: String
  def resolvedAt: Option[Instant]
  def resolvedBy: Option[DbRef[User]]
  def visibility: Visibility

  /** Check if the change has been dealt with */
  def isResolved: Boolean = resolvedAt.isDefined
}
