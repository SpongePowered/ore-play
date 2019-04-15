package models.querymodels
import java.sql.Timestamp

import models.admin.Review
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
