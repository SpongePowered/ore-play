package ore.permission.scope
import db.DbRef
import models.project.Project
import models.user.Organization

trait HasScope[-A] {

  def getScope(a: A): Scope
}
object HasScope {
  def apply[A](implicit hasScope: HasScope[A]): HasScope[A] = hasScope

  def orgScope[A](f: A => DbRef[Organization]): HasScope[A] = (a: A) => OrganizationScope(f(a))
  def projectScope[A](f: A => DbRef[Project]): HasScope[A]  = (a: A) => ProjectScope(f(a))
}
