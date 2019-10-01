package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.admin.Review
import ore.models.project.Version
import ore.models.user.User

import io.circe.Json

class ReviewTable(tag: Tag) extends ModelTable[Review](tag, "project_version_reviews") {

  def versionId = column[DbRef[Version]]("version_id")
  def userId    = column[DbRef[User]]("user_id")
  def endedAt   = column[OffsetDateTime]("ended_at")
  def comment   = column[Json]("comment")

  override def * =
    (id.?, createdAt.?, (versionId, userId, endedAt.?, comment)) <> (mkApply((Review.apply _).tupled), mkUnapply(
      Review.unapply
    ))
}
