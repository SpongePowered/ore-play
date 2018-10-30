package ore.project

import scala.concurrent.{ExecutionContext, Future}

import db.DbRef
import db.impl.access.ProjectBase
import models.project.Project

import cats.instances.future._

/**
  * Represents anything that has a [[models.project.Project]].
  */
trait ProjectOwned {

  /** Returns the Project ID */
  def projectId: DbRef[Project]

  /** Returns the Project */
  def project(implicit projects: ProjectBase, ec: ExecutionContext): Future[Project] =
    projects.get(this.projectId).getOrElse(throw new NoSuchElementException("Get on None"))
}
