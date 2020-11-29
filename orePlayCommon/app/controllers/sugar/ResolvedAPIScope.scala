package controllers.sugar

import ore.db.DbRef
import ore.models.organization.Organization
import ore.models.project.Project
import ore.permission.scope.{HasScope, Scope}

sealed abstract class ResolvedAPIScope {
  def toScope: Scope
}
object ResolvedAPIScope {
  case object GlobalScope extends ResolvedAPIScope {
    override def toScope: ore.permission.scope.GlobalScope.type = ore.permission.scope.GlobalScope
  }
  case class ProjectScope(projectOwner: String, projectSlug: String, id: DbRef[Project]) extends ResolvedAPIScope {
    override def toScope: ore.permission.scope.ProjectScope =
      ore.permission.scope.ProjectScope(id)
  }
  case class OrganizationScope(organizationName: String, id: DbRef[Organization]) extends ResolvedAPIScope {
    override def toScope: ore.permission.scope.OrganizationScope = ore.permission.scope.OrganizationScope(id)
  }

  implicit val hasScope: HasScope[ResolvedAPIScope] = _.toScope
}
