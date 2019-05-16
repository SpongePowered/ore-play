package ore.models.project

import scala.language.{higherKinds, implicitConversions}

import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}

import cats.MonadError
import simulacrum.typeclass

/**
  * Represents anything that has a [[ore.models.project.Project]].
  */
@typeclass trait ProjectOwned[A] {

  /** Returns the Project ID */
  def projectId(a: A): DbRef[Project]

  /** Returns the Project */
  def project[F[_]: ModelService](a: A)(implicit F: MonadError[F, Throwable]): F[Model[Project]] =
    ModelView.now(Project).get(projectId(a)).getOrElseF(F.raiseError(new NoSuchElementException("Get on None")))
}
object ProjectOwned {

  implicit def isProjectOwned[A](implicit isOwned: ProjectOwned[A]): ProjectOwned[Model[A]] =
    (a: Model[A]) => isOwned.projectId(a.obj)
}
