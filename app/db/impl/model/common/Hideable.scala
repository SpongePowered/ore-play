package db.impl.model.common

import scala.language.higherKinds

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityColumn
import db.{DbRef, Model, ModelService}
import models.project.Visibility
import models.user.User
import util.syntax._

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
  def visibilityChanges[V[_, _]: QueryView](
      view: V[ModelVisibilityChange#T, ModelVisibilityChange]
  ): V[ModelVisibilityChange#T, ModelVisibilityChange]

  def visibilityChangesByDate[V[_, _]: QueryView](
      view: V[ModelVisibilityChange#T, ModelVisibilityChange]
  ): V[ModelVisibilityChange#T, ModelVisibilityChange] =
    visibilityChanges(view).sortView(_.createdAt)

  def lastVisibilityChange[QOptRet, SRet[_]](
      view: ModelView[QOptRet, SRet, ModelVisibilityChange#T, ModelVisibilityChange]
  ): QOptRet = visibilityChangesByDate(view).filterView(_.resolvedAt.?.isEmpty).one

  def lastChangeRequest[QOptRet, SRet[_]](
      view: ModelView[QOptRet, SRet, ModelVisibilityChange#T, ModelVisibilityChange]
  ): QOptRet =
    visibilityChanges(view)
      .modifyingQuery(_.sortBy(_.createdAt.desc))
      .filterView(_.visibility === (Visibility.NeedsChanges: Visibility))
      .one

}
