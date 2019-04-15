package models.project

import java.sql.Timestamp
import java.time.Instant

import db.impl.DefaultModelCompanion
import db.impl.schema.FlagTable
import models.user.User
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.project.{FlagReason, ProjectOwned}
import ore.user.UserOwned

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a flag on a Project that requires staff attention.
  *
  * @param projectId    Project ID
  * @param userId       Reporter ID
  * @param reason       Reason for flag
  * @param isResolved   True if has been reviewed and resolved by staff member
  */
case class Flag(
    projectId: DbRef[Project],
    userId: DbRef[User],
    reason: FlagReason,
    comment: String,
    isResolved: Boolean = false,
    resolvedAt: Option[Timestamp] = None,
    resolvedBy: Option[DbRef[User]] = None
)
object Flag extends DefaultModelCompanion[Flag, FlagTable](TableQuery[FlagTable]) {

  implicit val query: ModelQuery[Flag] =
    ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[Flag] = (a: Flag) => a.projectId
  implicit val isUserOwned: UserOwned[Flag]       = (a: Flag) => a.userId

  implicit class FlagModelOps(private val self: Model[Flag]) extends AnyVal {

    /**
      * Sets whether this Flag has been marked as resolved.
      *
      * @param resolved True if resolved
      */
    def markResolved(
        resolved: Boolean,
        user: Option[Model[User]]
    )(implicit service: ModelService): IO[Model[Flag]] = {
      val (at, by) =
        if (resolved)
          (Some(Timestamp.from(Instant.now)), user.map(_.id.value): Option[DbRef[User]])
        else
          (None, None)

      service.update(self)(
        _.copy(
          isResolved = resolved,
          resolvedAt = at,
          resolvedBy = by
        )
      )
    }
  }
}
