package ore.project

import scala.language.implicitConversions

import scala.concurrent.{ExecutionContext, Future}

import db.DbRef
import db.impl.access.ProjectBase
import models.project.Project

import cats.instances.future._
import simulacrum.typeclass

/**
  * Represents anything that has a [[models.project.Project]].
  */
@typeclass trait ProjectOwned[A] {

  /** Returns the Project ID */
  def projectId(a: A): DbRef[Project]

  /** Returns the Project */
  def project(a: A)(implicit projects: ProjectBase, ec: ExecutionContext): Future[Project] =
    projects.get(projectId(a)).getOrElse(throw new NoSuchElementException("Get on None"))
}
