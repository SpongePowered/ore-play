package db.impl.schema

import java.sql.Timestamp

import play.api.i18n.Lang

import db.impl.OrePostgresDriver.api._
import db.impl.table.StatTable
import db.impl.table.common.{DescriptionColumn, DownloadsColumn, VisibilityChangeColumns, VisibilityColumn}
import db.table.{AssociativeTable, ModelTable, NameColumn}
import db.{ObjectId, ObjectReference}
import models.admin._
import models.api.ProjectApiKey
import models.project.{TagColor, _}
import models.statistic.{ProjectView, VersionDownload}
import models.user.role.{DbRole, OrganizationUserRole, ProjectUserRole, UserRoleModel}
import models.user.{
  LoggedAction,
  LoggedActionContext,
  LoggedActionModel,
  Notification,
  Organization,
  SignOn,
  User,
  Session => DbSession
}
import ore.Color
import ore.permission.role.{Role, RoleCategory, Trust}
import ore.project.io.DownloadType
import ore.project.{Category, FlagReason}
import ore.rest.ProjectApiKeyType
import ore.user.Prompt
import ore.user.notification.NotificationType

import cats.data.NonEmptyList
import com.github.tminglei.slickpg.InetString

/*
 * Database schema definitions. Changes must be first applied as an evolutions
 * SQL script in "conf/evolutions/default", then here, then in the associated
 * model.
 */

trait ProjectTable
    extends ModelTable[Project]
    with NameColumn[Project]
    with DownloadsColumn[Project]
    with VisibilityColumn[Project]
    with DescriptionColumn[Project] {

  def pluginId             = column[String]("plugin_id")
  def ownerName            = column[String]("owner_name")
  def userId               = column[ObjectReference]("owner_id")
  def slug                 = column[String]("slug")
  def recommendedVersionId = column[ObjectReference]("recommended_version_id")
  def category             = column[Category]("category")
  def stars                = column[Long]("stars")
  def views                = column[Long]("views")
  def topicId              = column[Option[Int]]("topic_id")
  def postId               = column[Int]("post_id")
  def isTopicDirty         = column[Boolean]("is_topic_dirty")
  def lastUpdated          = column[Timestamp]("last_updated")
  def notes                = column[String]("notes")

  override def * = {
    val convertedUnapply = convertUnapply(Project.unapply)
    (
      id.?,
      createdAt.?,
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
    ) <> (convertApply(Project.apply _).tupled, convertedUnapply)
  }

}

class ProjectTableMain(tag: Tag) extends ModelTable[Project](tag, "projects") with ProjectTable

//class ProjectTableDeleted(tag: Tag) extends ModelTable[Project](tag, "projects_deleted") with ProjectTable

class ProjectSettingsTable(tag: Tag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId   = column[ObjectReference]("project_id")
  def homepage    = column[String]("homepage")
  def issues      = column[String]("issues")
  def source      = column[String]("source")
  def licenseName = column[String]("license_name")
  def licenseUrl  = column[String]("license_url")
  def forumSync   = column[Boolean]("forum_sync")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectSettings.unapply)
    (id.?, createdAt.?, projectId, homepage.?, issues.?, source.?, licenseName.?, licenseUrl.?, forumSync) <> (convertApply(
      ProjectSettings.apply _
    ).tupled, convertedUnapply)
  }

}

class ProjectWatchersTable(tag: Tag)
    extends AssociativeTable(tag, "project_watchers", classOf[Project], classOf[User]) {

  def projectId = column[ObjectReference]("project_id")
  def userId    = column[ObjectReference]("user_id")

  override def * = (projectId, userId)

}

class ProjectViewsTable(tag: Tag) extends StatTable[ProjectView](tag, "project_views", "project_id") {

  override def * = {
    val convertedUnapply = convertUnapply(ProjectView.unapply)
    (id.?, createdAt.?, modelId, address, cookie, userId.?) <> (convertApply(ProjectView.apply _).tupled, convertedUnapply)
  }

}

class ProjectStarsTable(tag: Tag) extends AssociativeTable(tag, "project_stars", classOf[User], classOf[Project]) {

  def userId    = column[ObjectReference]("user_id")
  def projectId = column[ObjectReference]("project_id")

  override def * = (userId, projectId)

}

class ProjectLogTable(tag: Tag) extends ModelTable[ProjectLog](tag, "project_logs") {

  def projectId = column[ObjectReference]("project_id")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectLog.unapply)
    (id.?, createdAt.?, projectId) <> (convertApply(ProjectLog.apply _).tupled, convertedUnapply)
  }

}

class ProjectLogEntryTable(tg: Tag) extends ModelTable[ProjectLogEntry](tg, "project_log_entries") {

  def logId          = column[ObjectReference]("log_id")
  def tag            = column[String]("tag")
  def message        = column[String]("message")
  def occurrences    = column[Int]("occurrences")
  def lastOccurrence = column[Timestamp]("last_occurrence")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectLogEntry.unapply)
    (id.?, createdAt.?, logId, tag, message, occurrences, lastOccurrence) <> (convertApply(ProjectLogEntry.apply _).tupled,
    convertedUnapply)
  }

}

class PageTable(tag: Tag) extends ModelTable[Page](tag, "project_pages") with NameColumn[Page] {

  def projectId   = column[ObjectReference]("project_id")
  def parentId    = column[Option[ObjectReference]]("parent_id")
  def slug        = column[String]("slug")
  def contents    = column[String]("contents")
  def isDeletable = column[Boolean]("is_deletable")

  override def * = {
    val convertedUnapply = convertUnapply(Page.unapply)
    (id.?, createdAt.?, projectId, parentId, name, slug, isDeletable, contents) <> (convertApply(Page.apply _).tupled, convertedUnapply)
  }

}

class ChannelTable(tag: Tag) extends ModelTable[Channel](tag, "project_channels") with NameColumn[Channel] {

  def color         = column[Color]("color")
  def projectId     = column[ObjectReference]("project_id")
  def isNonReviewed = column[Boolean]("is_non_reviewed")

  override def * = {
    val convertedUnapply = convertUnapply(Channel.unapply)
    (id.?, createdAt.?, projectId, name, color, isNonReviewed) <> (convertApply(Channel.apply _).tupled,
    convertedUnapply)
  }
}

class VersionTagTable(tag: Tag)
    extends ModelTable[VersionTag](tag, "project_version_tags")
    with NameColumn[VersionTag] {

  def versionId = column[ObjectReference]("version_id")
  def data      = column[String]("data")
  def color     = column[TagColor]("color")

  override def * = {
    val convertedApply: ((Option[ObjectReference], ObjectReference, String, String, TagColor)) => VersionTag = {
      case (id, versionIds, name, data, color) =>
        VersionTag(ObjectId.unsafeFromOption(id), versionIds, name, data, color)
    }
    val convertedUnapply
      : PartialFunction[VersionTag, (Option[ObjectReference], ObjectReference, String, String, TagColor)] = {
      case VersionTag(id, versionIds, name, data, color) => (id.unsafeToOption, versionIds, name, data, color)
    }
    (id.?, versionId, name, data, color) <> (convertedApply, convertedUnapply.lift)
  }
}

class VersionTable(tag: Tag)
    extends ModelTable[Version](tag, "project_versions")
    with DownloadsColumn[Version]
    with DescriptionColumn[Version]
    with VisibilityColumn[Version] {

  def versionString     = column[String]("version_string")
  def dependencies      = column[List[String]]("dependencies")
  def assets            = column[String]("assets")
  def projectId         = column[ObjectReference]("project_id")
  def channelId         = column[ObjectReference]("channel_id")
  def fileSize          = column[Long]("file_size")
  def hash              = column[String]("hash")
  def authorId          = column[ObjectReference]("author_id")
  def reviewStatus      = column[ReviewState]("review_state")
  def reviewerId        = column[ObjectReference]("reviewer_id")
  def approvedAt        = column[Timestamp]("approved_at")
  def fileName          = column[String]("file_name")
  def signatureFileName = column[String]("signature_file_name")

  override def * = {
    val convertedUnapply = convertUnapply(Version.unapply)
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
      reviewStatus,
      reviewerId.?,
      approvedAt.?,
      visibility,
      fileName,
      signatureFileName
    ) <> (convertApply(Version.apply _).tupled, convertedUnapply)
  }
}

class DownloadWarningsTable(tag: Tag) extends ModelTable[DownloadWarning](tag, "project_version_download_warnings") {

  def expiration  = column[Timestamp]("expiration")
  def token       = column[String]("token")
  def versionId   = column[ObjectReference]("version_id")
  def address     = column[InetString]("address")
  def downloadId  = column[ObjectReference]("download_id")
  def isConfirmed = column[Boolean]("is_confirmed")

  override def * = {
    val convertedUnapply = convertUnapply(DownloadWarning.unapply)
    (id.?, createdAt.?, expiration, token, versionId, address, isConfirmed, downloadId.?) <> (convertApply(
      DownloadWarning.apply _
    ).tupled, convertedUnapply)
  }

}

class UnsafeDownloadsTable(tag: Tag) extends ModelTable[UnsafeDownload](tag, "project_version_unsafe_downloads") {

  def userId       = column[ObjectReference]("user_id")
  def address      = column[InetString]("address")
  def downloadType = column[DownloadType]("download_type")

  override def * = {
    val convertedUnapply = convertUnapply(UnsafeDownload.unapply)
    (id.?, createdAt.?, userId.?, address, downloadType) <> (convertApply(UnsafeDownload.apply _).tupled,
    convertedUnapply)
  }

}

class VersionDownloadsTable(tag: Tag)
    extends StatTable[VersionDownload](tag, "project_version_downloads", "version_id") {

  override def * = {
    val convertedUnapply = convertUnapply(VersionDownload.unapply)
    (id.?, createdAt.?, modelId, address, cookie, userId.?) <> (convertApply(VersionDownload.apply _).tupled,
    convertedUnapply)
  }

}

class UserTable(tag: Tag) extends ModelTable[User](tag, "users") with NameColumn[User] {

  // Override to remove auto increment
  override def id = column[ObjectReference]("id", O.PrimaryKey)

  def fullName            = column[String]("full_name")
  def email               = column[String]("email")
  def pgpPubKey           = column[String]("pgp_pub_key")
  def lastPgpPubKeyUpdate = column[Timestamp]("last_pgp_pub_key_update")
  def isLocked            = column[Boolean]("is_locked")
  def tagline             = column[String]("tagline")
  def joinDate            = column[Timestamp]("join_date")
  def readPrompts         = column[List[Prompt]]("read_prompts")
  def lang                = column[Lang]("language")

  override def * = {
    val convertedUnapply = convertUnapply(User.unapply)
    (
      id.?,
      createdAt.?,
      fullName.?,
      name,
      email.?,
      tagline.?,
      joinDate.?,
      readPrompts,
      pgpPubKey.?,
      lastPgpPubKeyUpdate.?,
      isLocked,
      lang.?
    ) <> (convertApply(User.apply _).tupled,
    convertedUnapply)
  }
}

class SessionTable(tag: Tag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[Timestamp]("expiration")
  def username   = column[String]("username")
  def token      = column[String]("token")

  def * = {
    val convertedUnapply = convertUnapply(DbSession.unapply)
    (id.?, createdAt.?, expiration, username, token) <> (convertApply(DbSession.apply _).tupled, convertedUnapply)
  }

}

class SignOnTable(tag: Tag) extends ModelTable[SignOn](tag, "user_sign_ons") {

  def nonce       = column[String]("nonce")
  def isCompleted = column[Boolean]("is_completed")

  def * = {
    val convertedUnapply = convertUnapply(SignOn.unapply)
    (id.?, createdAt.?, nonce, isCompleted) <> (convertApply(SignOn.apply _).tupled, convertedUnapply)
  }

}

class OrganizationTable(tag: Tag) extends ModelTable[Organization](tag, "organizations") with NameColumn[Organization] {

  override def id = column[ObjectReference]("id", O.PrimaryKey)
  def userId      = column[ObjectReference]("user_id")

  override def * = {
    val convertedUnapply = convertUnapply(Organization.unapply)
    (id.?, createdAt.?, name, userId) <> (convertApply(Organization.apply _).tupled, convertedUnapply)
  }

}

class OrganizationMembersTable(tag: Tag)
    extends AssociativeTable(tag, "organization_members", classOf[User], classOf[Organization]) {

  def userId         = column[ObjectReference]("user_id")
  def organizationId = column[ObjectReference]("organization_id")

  override def * = (userId, organizationId)

}

trait RoleTable[R <: UserRoleModel] extends ModelTable[R] {

  def userId     = column[ObjectReference]("user_id")
  def roleType   = column[Role]("role_type")
  def isAccepted = column[Boolean]("is_accepted")

}

class OrganizationRoleTable(tag: Tag)
    extends ModelTable[OrganizationUserRole](tag, "user_organization_roles")
    with RoleTable[OrganizationUserRole] {

  def organizationId = column[ObjectReference]("organization_id")

  override def * = {
    val convertedUnapply = convertUnapply(OrganizationUserRole.unapply)
    (id.?, createdAt.?, userId, organizationId, roleType, isAccepted) <> (convertApply(OrganizationUserRole.apply _).tupled, convertedUnapply)
  }

}

class ProjectRoleTable(tag: Tag)
    extends ModelTable[ProjectUserRole](tag, "user_project_roles")
    with RoleTable[ProjectUserRole] {

  def projectId = column[ObjectReference]("project_id")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectUserRole.unapply)
    (id.?, createdAt.?, userId, projectId, roleType, isAccepted) <> (convertApply(ProjectUserRole.apply _).tupled,
    convertedUnapply)
  }

}

class ProjectMembersTable(tag: Tag) extends AssociativeTable(tag, "project_members", classOf[Project], classOf[User]) {

  def projectId = column[ObjectReference]("project_id")
  def userId    = column[ObjectReference]("user_id")

  override def * = (projectId, userId)

}

class NotificationTable(tag: Tag) extends ModelTable[Notification](tag, "notifications") {

  def userId           = column[ObjectReference]("user_id")
  def originId         = column[ObjectReference]("origin_id")
  def notificationType = column[NotificationType]("notification_type")
  def messageArgs      = column[NonEmptyList[String]]("message_args")
  def action           = column[String]("action")
  def read             = column[Boolean]("read")

  override def * = {
    val convertedUnapply = convertUnapply(Notification.unapply)
    (id.?, createdAt.?, userId, originId, notificationType, messageArgs, action.?, read) <> (convertApply(
      Notification.apply _
    ).tupled, convertedUnapply)
  }
}

class FlagTable(tag: Tag) extends ModelTable[Flag](tag, "project_flags") {

  def projectId  = column[ObjectReference]("project_id")
  def userId     = column[ObjectReference]("user_id")
  def reason     = column[FlagReason]("reason")
  def comment    = column[String]("comment")
  def isResolved = column[Boolean]("is_resolved")
  def resolvedAt = column[Timestamp]("resolved_at")
  def resolvedBy = column[ObjectReference]("resolved_by")

  override def * = {
    val convertedUnapply = convertUnapply(Flag.unapply)
    (id.?, createdAt.?, projectId, userId, reason, comment, isResolved, resolvedAt.?, resolvedBy.?) <> (convertApply(
      Flag.apply _
    ).tupled, convertedUnapply)
  }

}

class ProjectApiKeyTable(tag: Tag) extends ModelTable[ProjectApiKey](tag, "project_api_keys") {

  def projectId = column[ObjectReference]("project_id")
  def keyType   = column[ProjectApiKeyType]("key_type")
  def value     = column[String]("value")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectApiKey.unapply)
    (id.?, createdAt.?, projectId, keyType, value) <> (convertApply(ProjectApiKey.apply _).tupled, convertedUnapply)
  }

}

class ReviewTable(tag: Tag) extends ModelTable[Review](tag, "project_version_reviews") {

  def versionId = column[ObjectReference]("version_id")
  def userId    = column[ObjectReference]("user_id")
  def endedAt   = column[Timestamp]("ended_at")
  def comment   = column[String]("comment")

  override def * = {
    val convertedUnapply = convertUnapply(Review.unapply)
    (id.?, createdAt.?, versionId, userId, endedAt.?, comment) <> (convertApply(Review.apply _).tupled, convertedUnapply)
  }
}

class ProjectVisibilityChangeTable(tag: Tag)
    extends ModelTable[ProjectVisibilityChange](tag, "project_visibility_changes")
    with VisibilityChangeColumns[ProjectVisibilityChange] {

  def projectId = column[ObjectReference]("project_id")

  override def * = {
    val convertedUnapply = convertUnapply(ProjectVisibilityChange.unapply)
    (id.?, createdAt.?, createdBy.?, projectId, comment, resolvedAt.?, resolvedBy.?, visibility) <> (convertApply(
      ProjectVisibilityChange.apply _
    ).tupled, convertedUnapply)
  }
}

class LoggedActionTable(tag: Tag) extends ModelTable[LoggedActionModel](tag, "logged_actions") {

  def userId          = column[ObjectReference]("user_id")
  def address         = column[InetString]("address")
  def action          = column[LoggedAction]("action")
  def actionContext   = column[LoggedActionContext]("action_context")
  def actionContextId = column[ObjectReference]("action_context_id")
  def newState        = column[String]("new_state")
  def oldState        = column[String]("old_state")

  override def * = {
    val convertedUnapply = convertUnapply(LoggedActionModel.unapply)
    (id.?, createdAt.?, userId, address, action, actionContext, actionContextId, newState, oldState) <> (convertApply(
      LoggedActionModel.apply _
    ).tupled, convertedUnapply)
  }
}
class VersionVisibilityChangeTable(tag: Tag)
    extends ModelTable[VersionVisibilityChange](tag, "project_version_visibility_changes")
    with VisibilityChangeColumns[VersionVisibilityChange] {

  def versionId = column[ObjectReference]("version_id")

  override def * = {
    val convertedUnapply = convertUnapply(VersionVisibilityChange.unapply)
    (id.?, createdAt.?, createdBy.?, versionId, comment, resolvedAt.?, resolvedBy.?, visibility) <> (convertApply(
      VersionVisibilityChange.apply _
    ).tupled, convertedUnapply)
  }
}

class LoggedActionViewTable(tag: Tag) extends ModelTable[LoggedActionViewModel](tag, "v_logged_actions") {

  def userId          = column[ObjectReference]("user_id")
  def address         = column[InetString]("address")
  def action          = column[LoggedAction]("action")
  def actionContext   = column[LoggedActionContext]("action_context")
  def actionContextId = column[ObjectReference]("action_context_id")
  def newState        = column[String]("new_state")
  def oldState        = column[String]("old_state")
  def uId             = column[ObjectReference]("u_id")
  def uName           = column[String]("u_name")
  def pId             = column[ObjectReference]("p_id")
  def pPluginId       = column[String]("p_plugin_id")
  def pSlug           = column[String]("p_slug")
  def pOwnerName      = column[String]("p_owner_name")
  def pvId            = column[ObjectReference]("pv_id")
  def pvVersionString = column[String]("pv_version_string")
  def ppId            = column[ObjectReference]("pp_id")
  def ppSlug          = column[String]("pp_slug")
  def sId             = column[ObjectReference]("s_id")
  def sName           = column[String]("s_name")
  def filterProject   = column[ObjectReference]("filter_project")
  def filterVersion   = column[ObjectReference]("filter_version")
  def filterPage      = column[ObjectReference]("filter_page")
  def filterSubject   = column[ObjectReference]("filter_subject")
  def filterAction    = column[Int]("filter_action")

  override def * = {
    val convertedUnapply = convertUnapply(LoggedActionViewModel.unapply)
    (
      id.?,
      createdAt.?,
      userId,
      address,
      action,
      actionContext,
      actionContextId,
      newState,
      oldState,
      uId,
      uName,
      loggedProjectProjection,
      loggedProjectVersionProjection,
      loggedProjectPageProjection,
      loggedSubjectProjection,
      filterProject.?,
      filterVersion.?,
      filterPage.?,
      filterSubject.?,
      filterAction.?
    ) <> (convertApply(LoggedActionViewModel.apply _).tupled, convertedUnapply)
  }

  def loggedProjectProjection =
    (pId.?, pPluginId.?, pSlug.?, pOwnerName.?) <> ((LoggedProject.apply _).tupled, LoggedProject.unapply)
  def loggedProjectVersionProjection =
    (pvId.?, pvVersionString.?) <> ((LoggedProjectVersion.apply _).tupled, LoggedProjectVersion.unapply)
  def loggedProjectPageProjection =
    (ppId.?, ppSlug.?) <> ((LoggedProjectPage.apply _).tupled, LoggedProjectPage.unapply)
  def loggedSubjectProjection = (sId.?, sName.?) <> ((LoggedSubject.apply _).tupled, LoggedSubject.unapply)
}

class DbRoleTable(tag: Tag) extends ModelTable[DbRole](tag, "roles") {
  def name         = column[String]("name")
  def category     = column[RoleCategory]("category")
  def trust        = column[Trust]("trust")
  def title        = column[String]("title")
  def color        = column[String]("color")
  def isAssignable = column[Boolean]("is_assignable")
  def rank         = column[Int]("rank")

  override def * = {
    val applyFunc
      : ((Option[ObjectReference], String, RoleCategory, Trust, String, String, Boolean, Option[Int])) => DbRole = {
      case (id, name, category, trust, title, color, isAssignable, rank) =>
        DbRole(ObjectId.unsafeFromOption(id), name, category, trust, title, color, isAssignable, rank)
    }

    val unapplyFunc: DbRole => Option[
      (Option[ObjectReference], String, RoleCategory, Trust, String, String, Boolean, Option[Int])
    ] = {
      case DbRole(id, name, category, trust, title, color, isAssignable, rank) =>
        Some((id.unsafeToOption, name, category, trust, title, color, isAssignable, rank))
    }

    (id.?, name, category, trust, title, color, isAssignable, rank.?) <> (applyFunc, unapplyFunc)
  }
}

class UserGlobalRolesTable(tag: Tag)
    extends AssociativeTable(tag, "user_global_roles", classOf[User], classOf[DbRole]) {

  def userId = column[ObjectReference]("user_id")
  def roleId = column[ObjectReference]("role_id")

  override def * = (userId, roleId)
}
