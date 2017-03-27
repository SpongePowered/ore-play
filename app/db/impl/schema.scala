package db.impl

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.{DescriptionColumn, DownloadsColumn, VisibilityColumn}
import db.impl.table.StatTable
import db.table.{AssociativeTable, ModelTable, NameColumn}
import models.admin.{ProjectLog, ProjectLogEntry}
import models.api.ProjectApiKey
import models.project._
import models.statistic.{ProjectView, VersionDownload}
import models.user.role.{OrganizationRole, ProjectRole, RoleModel}
import models.user.{Notification, Organization, SignOn, User, Session => DbSession}
import ore.Colors.Color
import ore.permission.role.RoleTypes.RoleType
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import ore.project.io.DownloadTypes.DownloadType
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType
import ore.user.Prompts.Prompt
import ore.user.notification.NotificationTypes.NotificationType

/*
 * Database schema definitions. Changes must be first applied as an evolutions
 * SQL script in "conf/evolutions/default", then here, then in the associated
 * model.
 */

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
  def isSpongePlugin        =   column[Boolean]("is_sponge_plugin")
  def isForgeMod            =   column[Boolean]("is_forge_mod")
  def stars                 =   column[Int]("stars")
  def views                 =   column[Int]("views")
  def topicId               =   column[Int]("topic_id")
  def postId                =   column[Int]("post_id")
  def isTopicDirty          =   column[Boolean]("is_topic_dirty")
  def lastUpdated           =   column[Timestamp]("last_updated")

  override def * = (id.?, createdAt.?, pluginId, ownerName, userId, name, slug, recommendedVersionId.?, category,
                    isSpongePlugin, isForgeMod, description.?, stars, views, downloads, topicId, postId, isTopicDirty,
                    isVisible, lastUpdated) <> ((Project.apply _).tupled, Project.unapply)

}

class ProjectTableMain(tag: Tag) extends ModelTable[Project](tag, "projects") with ProjectTable

//class ProjectTableDeleted(tag: Tag) extends ModelTable[Project](tag, "projects_deleted") with ProjectTable

class ProjectSettingsTable(tag: Tag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId             =   column[Int]("project_id")
  def homepage              =   column[String]("homepage")
  def issues                =   column[String]("issues")
  def source                =   column[String]("source")
  def licenseName           =   column[String]("license_name")
  def licenseUrl            =   column[String]("license_url")

  override def * = (id.?, createdAt.?, projectId, homepage.?, issues.?, source.?, licenseName.?,
                    licenseUrl.?) <> (ProjectSettings.tupled, ProjectSettings.unapply)

}

class ProjectWatchersTable(tag: Tag)
  extends AssociativeTable(tag, "project_watchers", classOf[Project], classOf[User]) {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")

  override def * = (projectId, userId)

}

class ProjectViewsTable(tag: Tag) extends StatTable[ProjectView](tag, "project_views", "project_id") {

  override def * = (id.?, createdAt.?, modelId, address, cookie,
                    userId.?) <> ((ProjectView.apply _).tupled, ProjectView.unapply)

}

class ProjectStarsTable(tag: Tag) extends AssociativeTable(tag, "project_stars", classOf[User], classOf[Project]) {

  def userId      =   column[Int]("user_id")
  def projectId   =   column[Int]("project_id")

  override def * = (userId, projectId)

}

class ProjectLogTable(tag: Tag) extends ModelTable[ProjectLog](tag, "project_logs") {

  def projectId = column[Int]("project_id")

  override def * = (id.?, createdAt.?, projectId) <> (ProjectLog.tupled, ProjectLog.unapply)

}

class ProjectLogEntryTable(tg: Tag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def logId = column[Int]("log_id")
  def tag = column[String]("tag")
  def message = column[String]("message")
  def occurrences = column[Int]("occurrences")
  def lastOccurrence = column[Timestamp]("last_occurrence")

  override def * = (id.?, createdAt.?, logId, tag, message, occurrences, lastOccurrence) <> (ProjectLogEntry.tupled,
                    ProjectLogEntry.unapply)

}

class PageTable(tag: Tag) extends ModelTable[Page](tag, "project_pages") with NameColumn[Page] {

  def projectId     =   column[Int]("project_id")
  def parentId      =   column[Int]("parent_id")
  def slug          =   column[String]("slug")
  def contents      =   column[String]("contents")
  def isDeletable   =   column[Boolean]("is_deletable")

  override def * = (id.?, createdAt.?, projectId, parentId, name, slug, isDeletable,
                    contents) <> ((Page.apply _).tupled, Page.unapply)

}

class ChannelTable(tag: Tag) extends ModelTable[Channel](tag, "project_channels") with NameColumn[Channel] {

  def color         = column[Color]("color")
  def projectId     = column[Int]("project_id")
  def isNonReviewed = column[Boolean]("is_non_reviewed")

  override def * = (id.?, createdAt.?, projectId, name, color, isNonReviewed) <> ((Channel.apply _).tupled,
                    Channel.unapply)
}

class VersionTable(tag: Tag) extends ModelTable[Version](tag, "project_versions")
  with DownloadsColumn[Version]
  with DescriptionColumn[Version] {

  def versionString     =   column[String]("version_string")
  def dependencies      =   column[List[String]]("dependencies")
  def assets            =   column[String]("assets")
  def projectId         =   column[Int]("project_id")
  def channelId         =   column[Int]("channel_id")
  def fileSize          =   column[Long]("file_size")
  def hash              =   column[String]("hash")
  def isReviewed        =   column[Boolean]("is_reviewed")
  def fileName          =   column[String]("file_name")
  def signatureFileName =   column[String]("signature_file_name")

  override def * = (id.?, createdAt.?, projectId, versionString, dependencies, assets.?, channelId,
                    fileSize, hash, description.?, downloads, isReviewed, fileName,
                    signatureFileName) <> ((Version.apply _).tupled, Version.unapply)
}

class DownloadWarningsTable(tag: Tag) extends ModelTable[DownloadWarning](tag, "project_version_download_warnings") {

  def expiration = column[Timestamp]("expiration")
  def token = column[String]("token")
  def versionId = column[Int]("version_id")
  def address = column[InetString]("address")
  def downloadId = column[Int]("download_id")
  def isConfirmed = column[Boolean]("is_confirmed")

  override def * = (id.?, createdAt.?, expiration, token, versionId, address, isConfirmed,
                    downloadId) <> ((DownloadWarning.apply _).tupled, DownloadWarning.unapply)

}

class UnsafeDownloadsTable(tag: Tag) extends ModelTable[UnsafeDownload](tag, "project_version_unsafe_downloads") {

  def userId = column[Int]("user_id")
  def address = column[InetString]("address")
  def downloadType = column[DownloadType]("download_type")

  override def * = (id.?, createdAt.?, userId.?, address, downloadType) <> (UnsafeDownload.tupled,
                    UnsafeDownload.unapply)

}

class VersionDownloadsTable(tag: Tag)
  extends StatTable[VersionDownload](tag, "project_version_downloads", "version_id") {

  override def * = (id.?, createdAt.?, modelId, address, cookie, userId.?) <> ((VersionDownload.apply _).tupled,
                    VersionDownload.unapply)

}

class UserTable(tag: Tag) extends ModelTable[User](tag, "users") with NameColumn[User] {

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

  override def * = (id.?, createdAt.?, fullName.?, name, email.?, tagline.?, globalRoles, joinDate.?,
                    avatarUrl.?, readPrompts, pgpPubKey.?, lastPgpPubKeyUpdate.?, isLocked) <> ((User.apply _).tupled,
                    User.unapply)

}

class SessionTable(tag: Tag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[Timestamp]("expiration")
  def username = column[String]("username")
  def token = column[String]("token")

  def * = (id.?, createdAt.?, expiration, username, token) <> (DbSession.tupled, DbSession.unapply)

}

class SignOnTable(tag: Tag) extends ModelTable[SignOn](tag, "user_sign_ons") {

  def nonce = column[String]("nonce")
  def isCompleted = column[Boolean]("is_completed")

  def * = (id.?, createdAt.?, nonce, isCompleted) <> (SignOn.tupled, SignOn.unapply)

}

class OrganizationTable(tag: Tag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id   =   column[Int]("id", O.PrimaryKey)
  def userId        =   column[Int]("user_id")

  override def * = (id.?, createdAt.?, name, userId) <> (Organization.tupled, Organization.unapply)

}

class OrganizationMembersTable(tag: Tag) extends AssociativeTable(tag, "organization_members", classOf[User],
  classOf[Organization]) {

  def userId          =   column[Int]("user_id")
  def organizationId  =   column[Int]("organization_id")

  override def * = (userId, organizationId)

}

trait RoleTable[R <: RoleModel] extends ModelTable[R] with VisibilityColumn[R] {

  def userId      =   column[Int]("user_id")
  def roleType    =   column[RoleType]("role_type")
  def isAccepted  =   column[Boolean]("is_accepted")

}

class OrganizationRoleTable(tag: Tag)
  extends ModelTable[OrganizationRole](tag, "user_organization_roles")
  with RoleTable[OrganizationRole] {

  def organizationId = column[Int]("organization_id")

  override def * = (id.?, createdAt.?, userId, organizationId, roleType, isAccepted,
                    isVisible) <> (OrganizationRole.tupled, OrganizationRole.unapply)

}

class ProjectRoleTable(tag: Tag)
  extends ModelTable[ProjectRole](tag, "user_project_roles")
  with RoleTable[ProjectRole] {

  def projectId = column[Int]("project_id")

  override def * = (id.?, createdAt.?, userId, projectId, roleType, isAccepted, isVisible) <> (ProjectRole.tupled,
                    ProjectRole.unapply)

}

class ProjectMembersTable(tag: Tag) extends AssociativeTable(tag, "project_members", classOf[Project], classOf[User]) {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")

  override def * = (projectId, userId)

}

class NotificationTable(tag: Tag) extends ModelTable[Notification](tag, "notifications") {

  def userId            =   column[Int]("user_id")
  def originId          =   column[Int]("origin_id")
  def notificationType  =   column[NotificationType]("notification_type")
  def message           =   column[String]("message")
  def action            =   column[String]("action")
  def read              =   column[Boolean]("read")

  override def * = (id.?, createdAt.?, userId, originId, notificationType, message, action.?,
                    read) <> (Notification.tupled, Notification.unapply)

}

class FlagTable(tag: Tag) extends ModelTable[Flag](tag, "project_flags") {

  def projectId   =   column[Int]("project_id")
  def userId      =   column[Int]("user_id")
  def reason      =   column[FlagReason]("reason")
  def comment     =   column[String]("comment")
  def isResolved  =   column[Boolean]("is_resolved")

  override def * = (id.?, createdAt.?, projectId, userId, reason, comment, isResolved) <> (Flag.tupled, Flag.unapply)

}

class ProjectApiKeyTable(tag: Tag) extends ModelTable[ProjectApiKey](tag, "project_api_keys") {

  def projectId = column[Int]("project_id")
  def keyType = column[ProjectApiKeyType]("key_type")
  def value = column[String]("value")

  override def * = (id.?, createdAt.?, projectId, keyType, value) <> (ProjectApiKey.tupled, ProjectApiKey.unapply)

}
