package db.impl.table.common

import db.impl.OrePostgresDriver.api._
import db.impl.model.common.StateClassifiable
import db.table.ModelTable
import models.project.ProjectStates.ProjectState

/**
  * Represents a column in a [[ModelTable]] representing the state of the
  * model.
  *
  * @tparam M Model type
  */
trait StateColumn[M <: StateClassifiable] extends ModelTable[M] {

  /**
    * Column definition of state. Returns the state
    *
    * @return State column
    */
  def state = column[ProjectState]("state")

}
