package models.querymodels
import java.sql.Timestamp

import ore.data.project.ProjectNamespace
import ore.models.project.Visibility

case class UnhealtyProject(
    namespace: ProjectNamespace,
    topicId: Option[Int],
    postId: Option[Int],
    lastUpdated: Timestamp,
    visibility: Visibility
)
