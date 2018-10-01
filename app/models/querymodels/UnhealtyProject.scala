package models.querymodels
import java.sql.Timestamp

import models.project.Visibility

case class UnhealtyProject(
    namespace: ProjectNamespace,
    topicId: Option[Int],
    postId: Option[Int],
    isTopicDirty: Boolean,
    lastUpdated: Timestamp,
    visibility: Visibility
)
