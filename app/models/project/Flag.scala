package models.project

import java.sql.Timestamp
import java.time.Instant

import db.impl.FlagTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import models.user.User
import ore.permission.scope.ProjectScope
import ore.project.FlagReasons.FlagReason
import ore.user.UserOwned

/**
  * Represents a flag on a Project that requires staff attention.
  *
  * @param id           Unique ID
  * @param createdAt    Timestamp instant of creation
  * @param projectId    Project ID
  * @param userId       Reporter ID
  * @param reason       Reason for flag
  * @param _isResolved  True if has been reviewed and resolved by staff member
  */
case class Flag(override val id: Option[Int],
                override val createdAt: Option[Timestamp],
                override val projectId: Int,
                override val userId: Int,
                reason: FlagReason,
                comment: String,
                private var _isResolved: Boolean = false,
                var resolvedAt: Option[Timestamp] = None,
                var resolvedBy: Option[Int] = None)
                extends OreModel(id, createdAt)
                  with UserOwned
                  with ProjectScope {

  override type M = Flag
  override type T = FlagTable

  def this(projectId: Int, userId: Int, reason: FlagReason, comment: String) = {
    this(id=None, createdAt=None, projectId=projectId, userId=userId, reason=reason, comment=comment)
  }

  /**
    * Returns true if this Flag has been reviewed and marked as resolved by a
    * staff member.
    *
    * @return True if resolved
    */
  def isResolved: Boolean = this._isResolved

  /**
    * Sets whether this Flag has been marked as resolved.
    *
    * @param resolved True if resolved
    */
  def setResolved(resolved: Boolean, user: Option[User]) = Defined {
    this._isResolved = resolved
    update(IsResolved)
    if (resolved) {
      this.resolvedAt = Some(Timestamp.from(Instant.now))
      update(ResolvedAt)
      this.resolvedBy = Some(user.flatMap(_.id).getOrElse(-1))
      update(ResolvedBy)
    }
  }

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)

}
