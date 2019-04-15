package ore.project

import scala.language.implicitConversions

import models.project.Project
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

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
object ProjectOwned {

  implicit def isProjectOwned[A](implicit isOwned: ProjectOwned[A]): ProjectOwned[Model[A]] =
    (a: Model[A]) => isOwned.projectId(a.obj)
}
