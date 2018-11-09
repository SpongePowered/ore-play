package models.querymodels
import db.ObjectReference
import models.project.Visibility
import ore.permission.scope.HasScope
import ore.project.ProjectOwned

case class VisibilityNeedApprovalProject(
    id: ObjectReference,
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    changeRequester: Option[String],
    hasPreviousChange: Boolean,
    lastChanger: Option[String]
) extends ProjectOwned {
  override def projectId: ObjectReference = id
}
object VisibilityNeedApprovalProject {
  implicit val hasScope: HasScope[VisibilityNeedApprovalProject] = HasScope.projectScope(_.projectId)
}

case class VisibilityWaitingProject(
    id: ObjectReference,
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    lastChanger: Option[String]
)