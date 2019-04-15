package ore.permission.scope

import db.DbRef
import models.project.Project
import models.user.Organization

import scala.language.implicitConversions

import simulacrum.typeclass

@typeclass trait HasScope[-A] {
  def scope(a: A): Scope
}
object HasScope {
  def orgScope[A](f: A => DbRef[Organization]): HasScope[A] = (a: A) => OrganizationScope(f(a))
  def projectScope[A](f: A => DbRef[Project]): HasScope[A]  = (a: A) => ProjectScope(f(a))
}
