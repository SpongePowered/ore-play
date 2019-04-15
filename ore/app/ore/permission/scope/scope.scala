package ore.permission.scope

import models.project.Project
import models.user.Organization
import ore.db.DbRef

sealed trait Scope
object Scope extends LowPriorityScope {
  implicit val globalScopeHasScope: HasScope[GlobalScope.type]        = (a: GlobalScope.type) => a
  implicit val projectScopeHasScope: HasScope[ProjectScope]           = (a: ProjectScope) => a
  implicit val organizationScopeHasScope: HasScope[OrganizationScope] = (a: OrganizationScope) => a
}
trait LowPriorityScope {
  implicit val scopeHasScope: HasScope[Scope] = (a: Scope) => a
}

case object GlobalScope                               extends Scope
case class ProjectScope(id: DbRef[Project])           extends Scope
case class OrganizationScope(id: DbRef[Organization]) extends Scope
