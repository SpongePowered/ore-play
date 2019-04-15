package ore.project

import db.impl.access.ProjectBase
import models.project.Project
import ore.db.Model

import cats.data.OptionT
import cats.effect.IO

/**
  * Represents a dependency to another plugin. Either on or not on Ore.
  *
  * @param pluginId   Unique plugin ID
  * @param version    Version of dependency
  */
case class Dependency(pluginId: String, version: String) {

  /**
    * Tries to resolve this dependency as a Project and returns the result.
    *
    * @return Project if dependency is on Ore, empty otherwise.
    */
  def project(implicit projects: ProjectBase): OptionT[IO, Model[Project]] =
    projects.withPluginId(this.pluginId)

}
