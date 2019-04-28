package ore.permission.scope

import scala.language.implicitConversions

import ore.db.{DbRef, Model}
import ore.models.organization.Organization
import ore.models.project.Project

import simulacrum.typeclass

@typeclass trait HasScope[-A] {
  def scope(a: A): Scope
}
object HasScope {
  def orgScope[A](f: A => DbRef[Organization]): HasScope[A] = (a: A) => OrganizationScope(f(a))
  def projectScope[A](f: A => DbRef[Project]): HasScope[A]  = (a: A) => ProjectScope(f(a))

  implicit def hasUnderlyingScope[A](implicit hasScope: HasScope[A]): HasScope[Model[A]] =
    (a: Model[A]) => hasScope.scope(a.obj)
}
