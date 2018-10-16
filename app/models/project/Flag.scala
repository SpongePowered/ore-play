package models.project

import java.sql.Timestamp
import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.FlagTable
import db.{Model, ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import models.user.User
import ore.project.{FlagReason, ProjectOwned}
import ore.user.UserOwned

import slick.lifted.TableQuery

/**
  * Represents a flag on a Project that requires staff attention.
  *
  * @param id           Unique ID
  * @param createdAt    Timestamp instant of creation
  * @param projectId    Project ID
  * @param userId       Reporter ID
  * @param reason       Reason for flag
  * @param isResolved   True if has been reviewed and resolved by staff member
  */
case class Flag(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: ObjectReference,
    userId: ObjectReference,
    reason: FlagReason,
    comment: String,
    isResolved: Boolean = false,
    resolvedAt: Option[Timestamp] = None,
    resolvedBy: Option[ObjectReference] = None
) extends Model
    with UserOwned
    with ProjectOwned {

  override type M = Flag
  override type T = FlagTable

  def this(projectId: ObjectReference, userId: ObjectReference, reason: FlagReason, comment: String) = {
    this(
      id = ObjectId.Uninitialized,
      createdAt = ObjectTimestamp.Uninitialized,
      projectId = projectId,
      userId = userId,
      reason = reason,
      comment = comment
    )
  }

  /**
    * Sets whether this Flag has been marked as resolved.
    *
    * @param resolved True if resolved
    */
  def markResolved(
      resolved: Boolean,
      user: Option[User]
  )(implicit ec: ExecutionContext, service: ModelService): Future[Flag] = Defined {
    val (at, by) =
      if (resolved)
        (Some(Timestamp.from(Instant.now)), Some(user.map(_.id.value).getOrElse(-1)): Option[ObjectReference])
      else
        (None, None)

    service.update(
      copy(
        isResolved = resolved,
        resolvedAt = at,
        resolvedBy = by
      )
    )
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Flag = this.copy(id = id, createdAt = theTime)
}
object Flag {
  implicit val query: ModelQuery[Flag] =
    ModelQuery.from[Flag](TableQuery[FlagTable])
}
