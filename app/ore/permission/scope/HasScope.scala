package ore.permission.scope

import scala.language.implicitConversions

import db.ObjectReference

import simulacrum.typeclass

@typeclass trait HasScope[-A] {
  def scope(a: A): Scope
}
object HasScope {
  def orgScope[A](f: A => ObjectReference): HasScope[A]     = (a: A) => OrganizationScope(f(a))
  def projectScope[A](f: A => ObjectReference): HasScope[A] = (a: A) => ProjectScope(f(a))
}
