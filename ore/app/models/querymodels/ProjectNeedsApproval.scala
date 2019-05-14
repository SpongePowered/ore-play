package models.querymodels
import ore.data.project.ProjectNamespace
import ore.models.project.Visibility

case class ProjectNeedsApproval(
    namespace: ProjectNamespace,
    visibility: Visibility,
    comment: String,
    changeRequester: String
)
