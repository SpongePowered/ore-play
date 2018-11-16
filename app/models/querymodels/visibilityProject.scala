package models.querymodels
import db.DbRef
import models.project.{Project, Visibility}
import ore.permission.scope.HasScope
import ore.project.ProjectOwned

case class VisibilityNeedApprovalProject(
    id: DbRef[Project],
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    changeRequester: Option[String],
    hasPreviousChange: Boolean,
    lastChanger: Option[String]
)
object VisibilityNeedApprovalProject {
  implicit val hasScope: HasScope[VisibilityNeedApprovalProject]           = HasScope.projectScope(_.id)
  implicit val isProjectOwned: ProjectOwned[VisibilityNeedApprovalProject] = (a: VisibilityNeedApprovalProject) => a.id
}

case class VisibilityWaitingProject(
    id: DbRef[Project],
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    lastChanger: Option[String]
)
