package models.querymodels
import models.project.Visibility

case class ProjectNeedsApproval(
    namespace: ProjectNamespace,
    visibility: Visibility,
    comment: String,
    changeRequester: String
)
