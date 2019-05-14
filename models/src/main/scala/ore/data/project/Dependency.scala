package ore.data.project

import scala.language.higherKinds

import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{Model, ModelService}
import ore.models.project.Project

import cats.data.OptionT

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
  def project[F[_]: ModelService]: OptionT[F, Model[Project]] =
    ModelView.now(Project).find(_.pluginId === pluginId)

}
