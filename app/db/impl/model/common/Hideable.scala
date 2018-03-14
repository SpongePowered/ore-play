package db.impl.model.common

import db.Model
import db.impl.table.common.VisibilityColumn
import models.project.VisibilityTypes.Visibility

/**
  * Represents a [[Model]] that has a toggleable visibility.
  */
trait Hideable extends Model { self =>

  override type M <: Hideable { type M = self.M }
  override type T <: VisibilityColumn[M]

  /**
    * Returns true if the [[Model]] is visible.
    *
    * @return True if model is visible
    */
  def visibility: Visibility

}
