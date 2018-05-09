package db.impl.model.common

import db.Model
import db.impl.table.common.StateColumn
import models.project.ProjectStates.ProjectState

/**
  * Represents a [[Model]] that has a declared state.
  */
trait StateClassifiable extends Model { self =>

  override type M <: StateClassifiable { type M = self.M }
  override type T <: StateColumn[M]

  /**
    * Returns the current state of the [[Model]].
    *
    * @return the current state
    */
  def state: ProjectState

}
