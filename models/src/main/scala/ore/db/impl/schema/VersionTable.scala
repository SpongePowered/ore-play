package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.{DescriptionColumn, VisibilityColumn}
import ore.models.project.{Project, ReviewState, TagColor, Version}
import ore.models.user.User

//noinspection MutatorLikeMethodIsParameterless
class VersionTable(tag: Tag)
    extends ModelTable[Version](tag, "project_versions")
    with DescriptionColumn[Version]
    with VisibilityColumn[Version] {

  def versionString      = column[String]("version_string")
  def dependencyIds      = column[List[String]]("dependency_ids")
  def dependencyVersions = column[List[String]]("dependency_versions")
  def projectId          = column[DbRef[Project]]("project_id")
  def fileSize           = column[Long]("file_size")
  def hash               = column[String]("hash")
  def authorId           = column[DbRef[User]]("author_id")
  def reviewStatus       = column[ReviewState]("review_state")
  def reviewerId         = column[DbRef[User]]("reviewer_id")
  def approvedAt         = column[OffsetDateTime]("approved_at")
  def fileName           = column[String]("file_name")
  def createForumPost    = column[Boolean]("create_forum_post")
  def postId             = column[Option[Int]]("post_id")
  def isPostDirty        = column[Boolean]("is_post_dirty")

  def usesMixin    = column[Boolean]("uses_mixin")
  def stability    = column[Version.Stability]("uses_mixin")
  def releaseType  = column[Version.ReleaseType]("uses_mixin")
  def channelName  = column[String]("uses_mixin")
  def channelColor = column[TagColor]("uses_mixin")

  def tags =
    (usesMixin, stability, releaseType.?, channelName.?, channelColor.?) <> (Version.VersionTags.tupled, Version.VersionTags.unapply)

  override def * =
    (
      id.?,
      createdAt.?,
      (
        projectId,
        versionString,
        dependencyIds,
        dependencyVersions,
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
        isPostDirty,
        tags
      )
    ) <> (mkApply((Version.apply _).tupled), mkUnapply(Version.unapply))
}
