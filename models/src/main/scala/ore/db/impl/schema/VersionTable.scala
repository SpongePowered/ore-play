package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.{DescriptionColumn, VisibilityColumn}
import ore.models.project.{Channel, Project, ReviewState, Version}
import ore.models.user.User

class VersionTable(tag: Tag)
    extends ModelTable[Version](tag, "project_versions")
    with DescriptionColumn[Version]
    with VisibilityColumn[Version] {

  def versionString   = column[String]("version_string")
  def dependencies    = column[List[String]]("dependencies")
  def projectId       = column[DbRef[Project]]("project_id")
  def channelId       = column[DbRef[Channel]]("channel_id")
  def fileSize        = column[Long]("file_size")
  def hash            = column[String]("hash")
  def authorId        = column[DbRef[User]]("author_id")
  def reviewStatus    = column[ReviewState]("review_state")
  def reviewerId      = column[DbRef[User]]("reviewer_id")
  def approvedAt      = column[OffsetDateTime]("approved_at")
  def fileName        = column[String]("file_name")
  def createForumPost = column[Boolean]("create_forum_post")
  def postId          = column[Option[Int]]("post_id")
  def isPostDirty     = column[Boolean]("is_post_dirty")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        projectId,
        versionString,
        dependencies,
        channelId,
        fileSize,
        hash,
        authorId.?,
        description.?,
        reviewStatus,
        reviewerId.?,
        approvedAt.?,
        visibility,
        fileName,
        createForumPost,
        postId,
        isPostDirty
      )
    ) <> (mkApply((Version.apply _).tupled), mkUnapply(Version.unapply))
}
