package db.impl.schema

import java.sql.Timestamp

import play.api.libs.json.JsValue

import db.impl.OrePostgresDriver.api._
import models.admin.Review
import models.project.Version
import models.user.User
import ore.db.DbRef

class ReviewTable(tag: Tag) extends ModelTable[Review](tag, "project_version_reviews") {

  def versionId = column[DbRef[Version]]("version_id")
  def userId    = column[DbRef[User]]("user_id")
  def endedAt   = column[Timestamp]("ended_at")
  def comment   = column[JsValue]("comment")

  override def * =
    (id.?, createdAt.?, (versionId, userId, endedAt.?, comment)) <> (mkApply((Review.apply _).tupled), mkUnapply(
      Review.unapply
    ))
}
