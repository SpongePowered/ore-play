package ore.permission.scope
import db.ObjectReference

trait HasScope[-A] {

  def getScope(a: A): Scope
}
object HasScope {
  def apply[A](implicit hasScope: HasScope[A]): HasScope[A] = hasScope

  def orgScope[A](f: A => ObjectReference): HasScope[A]     = (a: A) => OrganizationScope(f(a))
  def projectScope[A](f: A => ObjectReference): HasScope[A] = (a: A) => ProjectScope(f(a))
}
