package ore.db.impl.schema

import java.time.Instant

import ore.data.project.Category
import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.{DescriptionColumn, DownloadsColumn, NameColumn, VisibilityColumn}
import ore.models.project._
import ore.models.user.User

import io.circe.Json

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
  def lastUpdated          = column[Instant]("last_updated")
  def notes                = column[Json]("notes")

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
