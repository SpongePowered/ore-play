package db.impl.model.common

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityColumn
import db.{DbRef, Model, ModelService}
import models.project.Visibility
import models.user.User

import cats.data.OptionT
import cats.effect.{ContextShift, IO}

/**
  * Represents a [[Model]] that has a toggleable visibility.
  */
trait Hideable extends Model { self =>

  override type M <: Hideable { type M = self.M }
  override type T <: VisibilityColumn[M]
  type ModelVisibilityChange <: VisibilityChange { type M = ModelVisibilityChange }

  /**
    * Returns true if the [[Model]] is visible.
    *
    * @return True if model is visible
    */
  def visibility: Visibility

  def isDeleted: Boolean = visibility == Visibility.SoftDelete

  /**
    * Sets whether this project is visible.
    *
    * @param visibility True if visible
    */
  def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[(M, ModelVisibilityChange)]

  /**
    * Get VisibilityChanges
    */
  def visibilityChanges(implicit service: ModelService): ModelAccess[ModelVisibilityChange]

  def visibilityChangesByDate(implicit service: ModelService): IO[Seq[ModelVisibilityChange]] =
    visibilityChanges.sortedNow(_.createdAt)

  def lastVisibilityChange(
      implicit service: ModelService
  ): OptionT[IO, ModelVisibilityChange] =
    OptionT(service.runDBIO(visibilityChanges.sorted(_.createdAt, _.resolvedAt.?.isEmpty, limit = 1).result.headOption))

  def lastChangeRequest(implicit service: ModelService): OptionT[IO, ModelVisibilityChange] =
    OptionT(
      service.runDBIO(
        visibilityChanges
          .sorted(_.createdAt.desc, _.visibility === (Visibility.NeedsChanges: Visibility), limit = 1)
          .result
          .headOption
      )
    )

}
