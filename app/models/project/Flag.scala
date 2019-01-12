package models.project

import java.sql.Timestamp
import java.time.Instant

import db.impl.schema.FlagTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.user.User
import ore.project.{FlagReason, ProjectOwned}
import ore.user.UserOwned

import cats.effect.IO
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
    id: ObjId[Flag],
    createdAt: ObjectTimestamp,
    projectId: DbRef[Project],
    userId: DbRef[User],
    reason: FlagReason,
    comment: String,
    isResolved: Boolean,
    resolvedAt: Option[Timestamp],
    resolvedBy: Option[DbRef[User]]
) extends Model {

  override type M = Flag
  override type T = FlagTable

  /**
    * Sets whether this Flag has been marked as resolved.
    *
    * @param resolved True if resolved
    */
  def markResolved(
      resolved: Boolean,
      user: Option[User]
  )(implicit service: ModelService): IO[Flag] = {
    val (at, by) =
      if (resolved)
        (Some(Timestamp.from(Instant.now)), Some(user.map(_.id.value).getOrElse(-1L)): Option[DbRef[User]])
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
}
object Flag {
  def partial(
      projectId: DbRef[Project],
      userId: DbRef[User],
      reason: FlagReason,
      comment: String,
      isResolved: Boolean = false,
      resolvedAt: Option[Timestamp] = None,
      resolvedBy: Option[DbRef[User]] = None
  ): InsertFunc[Flag] =
    (id, time) => Flag(id, time, projectId, userId, reason, comment, isResolved, resolvedAt, resolvedBy)

  implicit val query: ModelQuery[Flag] =
    ModelQuery.from[Flag](TableQuery[FlagTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[Flag] = (a: Flag) => a.projectId
  implicit val isUserOwned: UserOwned[Flag]       = (a: Flag) => a.userId
}
