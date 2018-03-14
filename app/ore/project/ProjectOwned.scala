package ore.project

import db.impl.access.ProjectBase
import models.project.Project

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents anything that has a [[models.project.Project]].
  */
trait ProjectOwned {
  /** Returns the Project ID */
  def projectId: Int
  /** Returns the Project */
  def project(implicit projects: ProjectBase, ec: ExecutionContext): Future[Project] = projects.get(this.projectId).map(_.get)
}
