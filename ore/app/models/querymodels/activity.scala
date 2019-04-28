package models.querymodels
import java.time.Instant

import ore.data.project.ProjectNamespace
import ore.db.DbRef
import ore.models.admin.Review

case class ReviewActivity(
    endedAt: Option[Instant],
    id: DbRef[Review],
    project: ProjectNamespace
)

case class FlagActivity(
    resolvedAt: Option[Instant],
    project: ProjectNamespace
)
