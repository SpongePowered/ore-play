package db.impl

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString

import db.impl.OrePostgresDriver.api._
import db.impl.schema._
import db.impl.table.StatTable
import db.impl.table.common.{DescriptionColumn, DownloadsColumn, VisibilityChangeColumns, VisibilityColumn}
import db.table.{AssociativeTable, ModelTable, NameColumn}
import models.admin.{ProjectLog, ProjectLogEntry, ProjectVisibilityChange, Review, VersionVisibilityChange}
import models.api.ProjectApiKey
import models.project.TagColors.TagColor
import models.project._
import models.statistic.{ProjectView, VersionDownload}
import models.user.role.{OrganizationRole, ProjectRole, RoleModel}
import models.user.{LoggedActionContext, Notification, Organization, SignOn, User, LoggedAction, LoggedActionModel, Session => DbSession}
import ore.Colors.Color
import ore.permission.role.RoleTypes.RoleType
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import ore.project.io.DownloadTypes.DownloadType
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType
import ore.user.Prompts.Prompt
import ore.user.notification.NotificationTypes.NotificationType
import play.api.i18n.Lang

/*
 * Database schema definitions. Changes must be first applied as an evolutions
 * SQL script in "conf/evolutions/default", then here, then in the associated
 * model.
 */

// Alias Slick's Tag type because we have our own Tag type
package object schema {
  type RowTag = slick.lifted.Tag
  type ProjectTag = models.project.Tag
}

trait ProjectTable extends ModelTable[Project]
  with NameColumn[Project]
  with DownloadsColumn[Project]
  with VisibilityColumn[Project]
  with DescriptionColumn[Project] {

  def pluginId              =   column[String]("plugin_id")
  def ownerName             =   column[String]("owner_name")
  def userId                =   column[Int]("owner_id")
  def slug                  =   column[String]("slug")
  def recommendedVersionId  =   column[Int]("recommended_version_id")
  def category              =   column[Category]("category")
  def stars                 =   column[Int]("stars")
  def views                 =   column[Int]("views")
  def topicId               =   column[Int]("topic_id")
  def postId                =   column[Int]("post_id")
  def isTopicDirty          =   column[Boolean]("is_topic_dirty")
  def lastUpdated           =   column[Timestamp]("last_updated")
  def notes                 =   column[String]("notes")

  override def * = (id.?, createdAt.?, pluginId, ownerName, userId, name, slug, recommendedVersionId.?, category,
                    description.?, stars, views, downloads, topicId, postId, isTopicDirty,
                    visibility, lastUpdated, notes) <> ((Project.apply _).tupled, Project.unapply)

}

class ProjectTableMain(tag: RowTag) extends ModelTable[Project](tag, "projects") with ProjectTable

//class ProjectTableDeleted(tag: RowTag) extends ModelTable[Project](tag, "projects_deleted") with ProjectTable

class ProjectSettingsTable(tag: RowTag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId             =   column[Int]("project_id")
  def homepage              =   column[String]("homepage")
  def issues                =   column[String]("issues")
  def source                =   column[String]("source")
  def licenseName           =   column[String]("license_name")
  def licenseUrl            =   column[String]("license_url")
  def forumSync             =   column[Boolean]("forum_sync")

  override def * = (id.?, createdAt.?, projectId, homepage.?, issues.?, source.?, licenseName.?,
                    licenseUrl.?, forumSync) <> (ProjectSettings.tupled, ProjectSettings.unapply)

}

class ProjectWatchersTable(tag: RowTag)
  extends AssociativeTable(tag, "project_watchers", classOf[Project], classOf[User]) {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")

  override def * = (projectId, userId)

}

class ProjectViewsTable(tag: RowTag) extends StatTable[ProjectView](tag, "project_views", "project_id") {

  override def * = (id.?, createdAt.?, modelId, address, cookie,
                    userId.?) <> ((ProjectView.apply _).tupled, ProjectView.unapply)

}

class ProjectStarsTable(tag: RowTag) extends AssociativeTable(tag, "project_stars", classOf[User], classOf[Project]) {

  def userId      =   column[Int]("user_id")
  def projectId   =   column[Int]("project_id")

  override def * = (userId, projectId)

}

class ProjectLogTable(tag: RowTag) extends ModelTable[ProjectLog](tag, "project_logs") {

  def projectId = column[Int]("project_id")

  override def * = (id.?, createdAt.?, projectId) <> (ProjectLog.tupled, ProjectLog.unapply)

}

class ProjectLogEntryTable(tg: RowTag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def logId = column[Int]("log_id")
  def tag = column[String]("tag")
  def message = column[String]("message")
  def occurrences = column[Int]("occurrences")
  def lastOccurrence = column[Timestamp]("last_occurrence")

  override def * = (id.?, createdAt.?, logId, tag, message, occurrences, lastOccurrence) <> (ProjectLogEntry.tupled,
                    ProjectLogEntry.unapply)

}

class PageTable(tag: RowTag) extends ModelTable[Page](tag, "project_pages") with NameColumn[Page] {

  def projectId     =   column[Int]("project_id")
  def parentId      =   column[Int]("parent_id")
  def slug          =   column[String]("slug")
  def contents      =   column[String]("contents")
  def isDeletable   =   column[Boolean]("is_deletable")

  override def * = (id.?, createdAt.?, projectId, parentId, name, slug, isDeletable,
                    contents) <> ((Page.apply _).tupled, Page.unapply)

}

class ChannelTable(tag: RowTag) extends ModelTable[Channel](tag, "project_channels") with NameColumn[Channel] {

  def color         = column[Color]("color")
  def projectId     = column[Int]("project_id")
  def isNonReviewed = column[Boolean]("is_non_reviewed")

  override def * = (id.?, createdAt.?, projectId, name, color, isNonReviewed) <> ((Channel.apply _).tupled,
                    Channel.unapply)
}

class TagTable(tag: RowTag) extends ModelTable[ProjectTag](tag, "project_tags") with NameColumn[ProjectTag] {

  def versionIds = column[List[Int]]("version_ids")
  def data       = column[String]("data")
  def color      = column[TagColor]("color")

  override def * = (id.?, versionIds, name, data, color) <> ((Tag.apply _).tupled, Tag.unapply)
}

class VersionTable(tag: RowTag) extends ModelTable[Version](tag, "project_versions")
  with DownloadsColumn[Version]
  with DescriptionColumn[Version]
  with VisibilityColumn[Version] {

  def versionString     =   column[String]("version_string")
  def dependencies      =   column[List[String]]("dependencies")
  def assets            =   column[String]("assets")
  def projectId         =   column[Int]("project_id")
  def channelId         =   column[Int]("channel_id")
  def fileSize          =   column[Long]("file_size")
  def hash              =   column[String]("hash")
  def authorId          =   column[Int]("author_id")
  def isReviewed        =   column[Boolean]("is_reviewed")
  def reviewerId        =   column[Int]("reviewer_id")
  def approvedAt        =   column[Timestamp]("approved_at")
  def fileName          =   column[String]("file_name")
  def signatureFileName =   column[String]("signature_file_name")
  def tagIds            =   column[List[Int]]("tags")

  override def * = (id.?, createdAt.?, projectId, versionString, dependencies, assets.?, channelId,
                    fileSize, hash, authorId, description.?, downloads, isReviewed, reviewerId, approvedAt.?,
                    tagIds, visibility, fileName, signatureFileName) <> ((Version.apply _).tupled, Version.unapply)
}

class DownloadWarningsTable(tag: RowTag) extends ModelTable[DownloadWarning](tag, "project_version_download_warnings") {

  def expiration = column[Timestamp]("expiration")
  def token = column[String]("token")
  def versionId = column[Int]("version_id")
  def address = column[InetString]("address")
  def downloadId = column[Int]("download_id")
  def isConfirmed = column[Boolean]("is_confirmed")

  override def * = (id.?, createdAt.?, expiration, token, versionId, address, isConfirmed,
                    downloadId) <> ((DownloadWarning.apply _).tupled, DownloadWarning.unapply)

}

class UnsafeDownloadsTable(tag: RowTag) extends ModelTable[UnsafeDownload](tag, "project_version_unsafe_downloads") {

  def userId = column[Int]("user_id")
  def address = column[InetString]("address")
  def downloadType = column[DownloadType]("download_type")

  override def * = (id.?, createdAt.?, userId.?, address, downloadType) <> (UnsafeDownload.tupled,
                    UnsafeDownload.unapply)

}

class VersionDownloadsTable(tag: RowTag)
  extends StatTable[VersionDownload](tag, "project_version_downloads", "version_id") {

  override def * = (id.?, createdAt.?, modelId, address, cookie, userId.?) <> ((VersionDownload.apply _).tupled,
                    VersionDownload.unapply)

}

class UserTable(tag: RowTag) extends ModelTable[User](tag, "users") with NameColumn[User] {

  // Override to remove auto increment
  override def id           =   column[Int]("id", O.PrimaryKey)

  def fullName              =   column[String]("full_name")
  def email                 =   column[String]("email")
  def pgpPubKey             =   column[String]("pgp_pub_key")
  def lastPgpPubKeyUpdate   =   column[Timestamp]("last_pgp_pub_key_update")
  def isLocked              =   column[Boolean]("is_locked")
  def tagline               =   column[String]("tagline")
  def globalRoles           =   column[List[RoleType]]("global_roles")
  def joinDate              =   column[Timestamp]("join_date")
  def avatarUrl             =   column[String]("avatar_url")
  def readPrompts           =   column[List[Prompt]]("read_prompts")
  def lang                  =   column[Lang]("language")

  override def * = (id.?, createdAt.?, fullName.?, name, email.?, tagline.?, globalRoles, joinDate.?,
                    avatarUrl.?, readPrompts, pgpPubKey.?, lastPgpPubKeyUpdate.?, isLocked, lang.?) <> ((User.apply _).tupled,
                    User.unapply)

}

class SessionTable(tag: RowTag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[Timestamp]("expiration")
  def username = column[String]("username")
  def token = column[String]("token")

  def * = (id.?, createdAt.?, expiration, username, token) <> (DbSession.tupled, DbSession.unapply)

}

class SignOnTable(tag: RowTag) extends ModelTable[SignOn](tag, "user_sign_ons") {

  def nonce = column[String]("nonce")
  def isCompleted = column[Boolean]("is_completed")

  def * = (id.?, createdAt.?, nonce, isCompleted) <> (SignOn.tupled, SignOn.unapply)

}

class OrganizationTable(tag: RowTag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id   =   column[Int]("id", O.PrimaryKey)
  def userId        =   column[Int]("user_id")

  override def * = (id.?, createdAt.?, name, userId) <> ((Organization.apply _).tupled, Organization.unapply)

}

class OrganizationMembersTable(tag: RowTag) extends AssociativeTable(tag, "organization_members", classOf[User],
  classOf[Organization]) {

  def userId          =   column[Int]("user_id")
  def organizationId  =   column[Int]("organization_id")

  override def * = (userId, organizationId)

}

trait RoleTable[R <: RoleModel] extends ModelTable[R] {

  def userId      =   column[Int]("user_id")
  def roleType    =   column[RoleType]("role_type")
  def isAccepted  =   column[Boolean]("is_accepted")

}

class OrganizationRoleTable(tag: RowTag)
  extends ModelTable[OrganizationRole](tag, "user_organization_roles")
  with RoleTable[OrganizationRole] {

  def organizationId = column[Int]("organization_id")

  override def * = (id.?, createdAt.?, userId, organizationId, roleType,
                    isAccepted) <> (OrganizationRole.tupled, OrganizationRole.unapply)

}

class ProjectRoleTable(tag: RowTag)
  extends ModelTable[ProjectRole](tag, "user_project_roles")
  with RoleTable[ProjectRole] {

  def projectId = column[Int]("project_id")

  override def * = (id.?, createdAt.?, userId, projectId, roleType, isAccepted) <> (ProjectRole.tupled,
                    ProjectRole.unapply)

}

class ProjectMembersTable(tag: RowTag) extends AssociativeTable(tag, "project_members", classOf[Project], classOf[User]) {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")

  override def * = (projectId, userId)

}

class NotificationTable(tag: RowTag) extends ModelTable[Notification](tag, "notifications") {

  def userId            =   column[Int]("user_id")
  def originId          =   column[Int]("origin_id")
  def notificationType  =   column[NotificationType]("notification_type")
  def messageArgs       =   column[List[String]]("message_args")
  def action            =   column[String]("action")
  def read              =   column[Boolean]("read")

  override def * = (id.?, createdAt.?, userId, originId, notificationType, messageArgs, action.?,
                    read) <> (Notification.tupled, Notification.unapply)

}

class FlagTable(tag: RowTag) extends ModelTable[Flag](tag, "project_flags") {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")
  def reason      =   column[FlagReason]("reason")
  def comment     =   column[String]("comment")
  def isResolved  =   column[Boolean]("is_resolved")
  def resolvedAt  =   column[Timestamp]("resolved_at")
  def resolvedBy  =   column[Int]("resolved_by")

  override def * = (id.?, createdAt.?, projectId, userId, reason, comment, isResolved, resolvedAt.?, resolvedBy.?) <> (Flag.tupled, Flag.unapply)

}

class ProjectApiKeyTable(tag: RowTag) extends ModelTable[ProjectApiKey](tag, "project_api_keys") {

  def projectId = column[Int]("project_id")
  def keyType = column[ProjectApiKeyType]("key_type")
  def value = column[String]("value")

  override def * = (id.?, createdAt.?, projectId, keyType, value) <> (ProjectApiKey.tupled, ProjectApiKey.unapply)

}

class ReviewTable(tag: RowTag) extends ModelTable[Review](tag, "project_version_reviews") {

  def versionId         =   column[Int]("version_id")
  def userId            =   column[Int]("user_id")
  def endedAt           =   column[Timestamp]("ended_at")
  def comment           =   column[String]("comment")

  override def * =  (id.?, createdAt.?, versionId, userId, endedAt.?, comment) <> ((Review.apply _).tupled, Review.unapply)
}

class ProjectVisibilityChangeTable(tag: RowTag)
  extends ModelTable[ProjectVisibilityChange](tag, "project_visibility_changes")
  with VisibilityChangeColumns[ProjectVisibilityChange] {

  def projectId = column[Int]("project_id")

  override def * = (id.?, createdAt.?, createdBy.?, projectId, comment, resolvedAt.?, resolvedBy.?, visibility) <> (ProjectVisibilityChange.tupled, ProjectVisibilityChange.unapply)
}

class LoggedActionTable(tag: RowTag) extends ModelTable[LoggedActionModel](tag, "logged_actions") {

  def userId             =  column[Int]("user_id")
  def address            =  column[InetString]("address")
  def action             =  column[LoggedAction]("action")
  def actionContext      =  column[LoggedActionContext]("action_context")
  def actionContextId      =  column[Int]("action_context_id")
  def newState           =  column[String]("new_state")
  def oldState           =  column[String]("old_state")

  override def * = (id.?, createdAt.?, userId, address, action, actionContext, actionContextId, newState, oldState) <> (LoggedActionModel.tupled, LoggedActionModel.unapply)
}
class VersionVisibilityChangeTable(tag: RowTag)
  extends ModelTable[VersionVisibilityChange](tag, "project_version_visibility_changes")
    with VisibilityChangeColumns[VersionVisibilityChange] {

  def versionId = column[Int]("version_id")

  override def * = (id.?, createdAt.?, createdBy.?, versionId, comment, resolvedAt.?, resolvedBy.?, visibility) <> (VersionVisibilityChange.tupled, VersionVisibilityChange.unapply)
}
