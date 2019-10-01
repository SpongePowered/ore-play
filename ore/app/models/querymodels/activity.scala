package models.querymodels
import java.time.OffsetDateTime

import ore.data.project.ProjectNamespace
import ore.db.DbRef
import ore.models.admin.Review

case class ReviewActivity(
    endedAt: Option[OffsetDateTime],
    id: DbRef[Review],
    project: ProjectNamespace
)

case class FlagActivity(
    resolvedAt: Option[OffsetDateTime],
    project: ProjectNamespace
)
