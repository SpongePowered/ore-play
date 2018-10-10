package ore.permission.scope

import ore.project.ProjectOwned

/**
  * Represents a scope of a certain [[models.project.Project]].
  */
trait ProjectScope extends Scope with ProjectOwned
