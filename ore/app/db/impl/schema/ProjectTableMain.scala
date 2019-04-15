package db.impl.schema

import java.sql.Timestamp

import play.api.libs.json.JsValue

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.{DescriptionColumn, DownloadsColumn, NameColumn, VisibilityColumn}
import db.table.ModelTable
import models.project._
import models.user.User
import ore.project.Category

trait ProjectTable
    extends ModelTable[Project]
    with NameColumn[Project]
    with DownloadsColumn[Project]
    with VisibilityColumn[Project]
    with DescriptionColumn[Project] {

  def pluginId             = column[String]("plugin_id")
  def ownerName            = column[String]("owner_name")
  def userId               = column[DbRef[User]]("owner_id")
  def slug                 = column[String]("slug")
  def recommendedVersionId = column[DbRef[Version]]("recommended_version_id")
  def category             = column[Category]("category")
  def stars                = column[Long]("stars")
  def views                = column[Long]("views")
  def topicId              = column[Option[Int]]("topic_id")
  def postId               = column[Int]("post_id")
  def isTopicDirty         = column[Boolean]("is_topic_dirty")
  def lastUpdated          = column[Timestamp]("last_updated")
  def notes                = column[JsValue]("notes")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        pluginId,
        ownerName,
        userId,
        name,
        slug,
        recommendedVersionId.?,
        category,
        description.?,
        stars,
        views,
        downloads,
        topicId,
        postId.?,
        isTopicDirty,
        visibility,
        lastUpdated,
        notes
      )
    ) <> (mkApply((Project.apply _).tupled), mkUnapply(Project.unapply))
}

class ProjectTableMain(tag: Tag) extends ModelTable[Project](tag, "projects") with ProjectTable
