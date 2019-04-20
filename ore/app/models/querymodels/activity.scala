package models.querymodels
import java.sql.Timestamp

import ore.admin.Review
import ore.data.project.ProjectNamespace
import ore.db.DbRef

case class ReviewActivity(
    endedAt: Option[Timestamp],
    id: DbRef[Review],
    project: ProjectNamespace
)

case class FlagActivity(
    resolvedAt: Option[Timestamp],
    project: ProjectNamespace
)
