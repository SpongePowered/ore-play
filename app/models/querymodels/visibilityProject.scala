package models.querymodels
import db.ObjectReference
import models.project.Visibility
import ore.permission.scope.ProjectScope

case class VisibilityNeedApprovalProject(
    id: ObjectReference,
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    changeRequester: Option[String],
    hasPreviousChange: Boolean,
    lastChanger: Option[String]
) extends ProjectScope {
  override def projectId: ObjectReference = id
}

case class VisibilityWaitingProject(
    id: ObjectReference,
    namespace: ProjectNamespace,
    visibility: Visibility,
    changeRequestComment: Option[String],
    lastChanger: Option[String]
)