package db.impl.model.common

import scala.language.higherKinds

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityChangeColumns
import db.{DbModel, DbRef, ModelService}
import models.project.Visibility
import models.user.User
import util.syntax._

import cats.effect.{ContextShift, IO}

/**
  * Represents a model that has a toggleable visibility.
  */
trait Hideable {

  /**
    * Returns true if the model is visible.
    *
    * @return True if model is visible
    */
  def visibility: Visibility

  def isDeleted: Boolean = visibility == Visibility.SoftDelete

}
trait HideableOps[M, MVisibilityChange <: VisibilityChange, MVisibilityChangeTable <: VisibilityChangeColumns[
  MVisibilityChange
]] extends Any {

  /**
    * Sets whether this project is visible.
    *
    * @param visibility True if visible
    */
  def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[(DbModel[M], DbModel[MVisibilityChange])]

  /**
    * Get VisibilityChanges
    */
  def visibilityChanges[V[_, _]: QueryView](
      view: V[MVisibilityChangeTable, DbModel[MVisibilityChange]]
  ): V[MVisibilityChangeTable, DbModel[MVisibilityChange]]

  def visibilityChangesByDate[V[_, _]: QueryView](
      view: V[MVisibilityChangeTable, DbModel[MVisibilityChange]]
  ): V[MVisibilityChangeTable, DbModel[MVisibilityChange]] =
    visibilityChanges(view).sortView(_.createdAt)

  def lastVisibilityChange[QOptRet, SRet[_]](
      view: ModelView[QOptRet, SRet, MVisibilityChangeTable, DbModel[MVisibilityChange]]
  ): QOptRet = visibilityChangesByDate(view).filterView(_.resolvedAt.?.isEmpty).one

  def lastChangeRequest[QOptRet, SRet[_]](
      view: ModelView[QOptRet, SRet, MVisibilityChangeTable, DbModel[MVisibilityChange]]
  ): QOptRet =
    visibilityChanges(view)
      .modifyingQuery(_.sortBy(_.createdAt.desc))
      .filterView(_.visibility === (Visibility.NeedsChanges: Visibility))
      .one
}
