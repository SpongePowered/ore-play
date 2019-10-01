package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.data.project.FlagReason
import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Flag, Project}
import ore.models.user.User

class FlagTable(tag: Tag) extends ModelTable[Flag](tag, "project_flags") {

  def projectId  = column[DbRef[Project]]("project_id")
  def userId     = column[DbRef[User]]("user_id")
  def reason     = column[FlagReason]("reason")
  def comment    = column[String]("comment")
  def isResolved = column[Boolean]("is_resolved")
  def resolvedAt = column[OffsetDateTime]("resolved_at")
  def resolvedBy = column[DbRef[User]]("resolved_by")

  override def * =
    (id.?, createdAt.?, (projectId, userId, reason, comment, isResolved, resolvedAt.?, resolvedBy.?)) <> (mkApply(
      (Flag.apply _).tupled
    ), mkUnapply(Flag.unapply))
}
