package db.impl.schema
import java.sql.Timestamp

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.{DescriptionColumn, DownloadsColumn, VisibilityColumn}
import db.table.ModelTable
import models.project.{Channel, Project, Version}
import models.user.User

class VersionTable(tag: RowTag)
    extends ModelTable[Version](tag, "project_versions")
    with DownloadsColumn[Version]
    with DescriptionColumn[Version]
    with VisibilityColumn[Version] {

  def versionString     = column[String]("version_string")
  def dependencies      = column[List[String]]("dependencies")
  def assets            = column[String]("assets")
  def projectId         = column[DbRef[Project]]("project_id")
  def channelId         = column[DbRef[Channel]]("channel_id")
  def fileSize          = column[Long]("file_size")
  def hash              = column[String]("hash")
  def authorId          = column[DbRef[User]]("author_id")
  def isReviewed        = column[Boolean]("is_reviewed")
  def reviewerId        = column[DbRef[User]]("reviewer_id")
  def approvedAt        = column[Timestamp]("approved_at")
  def fileName          = column[String]("file_name")
  def signatureFileName = column[String]("signature_file_name")
  def tagIds            = column[List[DbRef[ProjectTag]]]("tags")
  def isNonReviewed     = column[Boolean]("is_non_reviewed")

  override def * =
    mkProj(
      (
        id.?,
        createdAt.?,
        projectId,
        versionString,
        dependencies,
        assets.?,
        channelId,
        fileSize,
        hash,
        authorId,
        description.?,
        downloads,
        isReviewed,
        reviewerId.?,
        approvedAt.?,
        tagIds,
        visibility,
        fileName,
        signatureFileName,
        isNonReviewed
      )
    )(mkTuple[Version]())
}
