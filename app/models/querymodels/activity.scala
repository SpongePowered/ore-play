package models.querymodels
import java.sql.Timestamp

import db.ObjectReference

case class ReviewActivity(
    endedAt: Option[Timestamp],
    id: ObjectReference,
    project: ProjectNamespace
)

case class FlagActivity(
    resolvedAt: Option[Timestamp],
    project: ProjectNamespace
)
