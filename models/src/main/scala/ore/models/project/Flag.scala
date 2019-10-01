package ore.models.project

import scala.language.higherKinds

import java.time.OffsetDateTime

import ore.data.project.FlagReason
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.FlagTable
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.user.{User, UserOwned}

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
    resolvedAt: Option[OffsetDateTime] = None,
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
    def markResolved[F[_]](
        resolved: Boolean,
        user: Option[Model[User]]
    )(implicit service: ModelService[F]): F[Model[Flag]] = {
      val (at, by) =
        if (resolved)
          (Some(OffsetDateTime.now), user.map(_.id.value): Option[DbRef[User]])
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
