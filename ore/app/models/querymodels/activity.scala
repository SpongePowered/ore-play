package models.querymodels
import java.sql.Timestamp

import db.DbRef
import models.admin.Review

case class ReviewActivity(
    endedAt: Option[Timestamp],
    id: DbRef[Review],
    project: ProjectNamespace
)

case class FlagActivity(
    resolvedAt: Option[Timestamp],
    project: ProjectNamespace
)
