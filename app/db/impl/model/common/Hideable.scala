package db.impl.model.common

import scala.concurrent.{ExecutionContext, Future}

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityColumn
import db.{DbRef, Model, ModelService}
import models.project.Visibility
import models.user.User

import cats.data.OptionT

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
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[(M, ModelVisibilityChange)]

  /**
    * Get VisibilityChanges
    */
  def visibilityChanges(implicit service: ModelService): ModelAccess[ModelVisibilityChange]

  def visibilityChangesByDate(implicit service: ModelService): Future[Seq[ModelVisibilityChange]] =
    visibilityChanges.sorted(_.createdAt)

  def lastVisibilityChange(
      implicit ec: ExecutionContext,
      service: ModelService
  ): OptionT[Future, ModelVisibilityChange] =
    OptionT(visibilityChanges.sorted(_.createdAt, _.resolvedAt.?.isEmpty, limit = 1).map(_.headOption))

  def lastChangeRequest(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, ModelVisibilityChange] =
    OptionT(
      visibilityChanges
        .sorted(_.createdAt.desc, _.visibility === (Visibility.NeedsChanges: Visibility), limit = 1)
        .map(_.headOption)
    )

}
