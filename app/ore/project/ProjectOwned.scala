package ore.project

import scala.language.implicitConversions

import db.access.ModelView
import db.{Model, DbRef, ModelService}
import models.project.Project

import cats.effect.IO
import simulacrum.typeclass

/**
  * Represents anything that has a [[models.project.Project]].
  */
@typeclass trait ProjectOwned[A] {

  /** Returns the Project ID */
  def projectId(a: A): DbRef[Project]

  /** Returns the Project */
  def project(a: A)(implicit service: ModelService): IO[Model[Project]] =
    ModelView.now(Project).get(projectId(a)).getOrElseF(IO.raiseError(new NoSuchElementException("Get on None")))
}
