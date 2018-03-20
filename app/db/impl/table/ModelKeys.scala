package db.impl.table

import db.Named
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Describable, Downloadable}
import db.table.key._
import models.admin.{ProjectLogEntry, Review, VisibilityChange}
import models.project.VisibilityTypes.Visibility
import models.project._
import models.statistic.StatEntry
import models.user.role.RoleModel
import models.user.{Notification, Organization, SignOn, User}
import ore.Colors.Color
import ore.permission.role.RoleTypes.RoleType
import ore.project.Categories.Category
import ore.user.Prompts.Prompt

/**
  * Collection of String keys used for table bindings within Models.
  */
object ModelKeys {

  // Shared
  val Name                  =   new StringKey[Named](_.name, _.name)
  val Downloads             =   new IntKey[Downloadable](_.downloads, _.downloadCount)
  val Description           =   new StringKey[Describable](_.description, _.description.orNull)
  val Visibility            =   new MappedTypeKey[Project, Visibility](_.visibility, _.visibility)

  // Project
  val OwnerId               =   new IntKey[Project](_.userId, _.ownerId)
  val OwnerName             =   new StringKey[Project](_.ownerName, _.ownerName)
  val Slug                  =   new StringKey[Project](_.slug, _.slug)
  val Category              =   new MappedTypeKey[Project, Category](_.category, _.category)
  val Stars                 =   new IntKey[Project](_.stars, _.starCount)
  val Views                 =   new IntKey[Project](_.views, _.viewCount)
  val TopicId               =   new IntKey[Project](_.topicId, _.topicId)
  val PostId                =   new IntKey[Project](_.postId, _.postId)
  val IsTopicDirty          =   new BooleanKey[Project](_.isTopicDirty, _.isTopicDirty)
  val RecommendedVersionId  =   new IntKey[Project](_.recommendedVersionId, _.recommendedVersionId.getOrElse(-1))
  val LastUpdated           =   new TimestampKey[Project](_.lastUpdated, _.lastUpdated)
  val Notes                 =   new StringKey[Project](_.notes, _._notes)

  // ProjectSettings
  val Issues                =   new StringKey[ProjectSettings](_.issues, _.issues.orNull)
  val Source                =   new StringKey[ProjectSettings](_.source, _.source.orNull)
  val LicenseName           =   new StringKey[ProjectSettings](_.licenseName, _.licenseName.orNull)
  val LicenseUrl            =   new StringKey[ProjectSettings](_.licenseUrl, _.licenseUrl.orNull)
  val ForumSync             =   new BooleanKey[ProjectSettings](_.forumSync, _.forumSync)

  // ProjectLogEntry
  val Occurrences           =   new IntKey[ProjectLogEntry](_.occurrences, _.occurrences)
  val LastOccurrence        =   new TimestampKey[ProjectLogEntry](_.lastOccurrence, _.lastOccurrence)

  // User
  val FullName              =   new StringKey[User](_.fullName, _.fullName.orNull)
  val Email                 =   new StringKey[User](_.email, _.email.orNull)
  val PGPPubKey             =   new StringKey[User](_.pgpPubKey, _.pgpPubKey.orNull)
  val LastPGPPubKeyUpdate   =   new TimestampKey[User](_.lastPgpPubKeyUpdate, _.lastPgpPubKeyUpdate.orNull)
  val IsLocked              =   new BooleanKey[User](_.isLocked, _.isLocked)
  val Tagline               =   new StringKey[User](_.tagline, _.tagline.orNull)
  val GlobalRoles           =   new Key[User, List[RoleType]](_.globalRoles, _.globalRoles.toList)
  val JoinDate              =   new TimestampKey[User](_.joinDate, _.joinDate.orNull)
  val AvatarUrl             =   new StringKey[User](_.avatarUrl, _.avatarUrl.orNull)
  val ReadPrompts           =   new Key[User, List[Prompt]](_.readPrompts, _.readPrompts.toList)

  // Organization
  val OrgOwnerId            =   new IntKey[Organization](_.userId, _.owner.userId)

  // SignOn
  val IsCompleted           =   new BooleanKey[SignOn](_.isCompleted, _.isCompleted)

  // Version
  val IsReviewed            =   new BooleanKey[Version](_.isReviewed, _.isReviewed)
  val AuthorId              =   new IntKey[Version](_.authorId, _.authorId)
  val ReviewerId            =   new IntKey[Version](_.reviewerId, _.reviewerId)
  val ApprovedAt            =   new TimestampKey[Version](_.approvedAt, _.approvedAt.orNull)
  val ChannelId             =   new IntKey[Version](_.channelId, _.channelId)
  val TagIds                =   new Key[Version, List[Int]](_.tagIds, _.tagIds)

  // Tags
  val TagVersionIds           =   new Key[models.project.Tag, List[Int]](_.versionIds, _.versionIds)

  // DownloadWarning
  val DownloadId            =   new IntKey[DownloadWarning](_.downloadId, _.downloadId)
  val IsConfirmed           =   new BooleanKey[DownloadWarning](_.isConfirmed, _.isConfirmed)

  // Channel
  val Color                 =   new MappedTypeKey[Channel, Color](_.color, _.color)
  val IsNonReviewed         =   new BooleanKey[Channel](_.isNonReviewed, _.isNonReviewed)

  // Page
  val Contents              =   new StringKey[Page](_.contents, _.contents)

  // RoleModel
  val RoleType              =   new MappedTypeKey[RoleModel, RoleType](_.roleType, _.roleType)
  val IsAccepted            =   new BooleanKey[RoleModel](_.isAccepted, _.isAccepted)

  // Flag
  val IsResolved            =   new BooleanKey[Flag](_.isResolved, _.isResolved)
  val ResolvedAt            =   new TimestampKey[Flag](_.resolvedAt, _.resolvedAt.orNull)
  val ResolvedBy            =   new IntKey[Flag](_.resolvedBy, _.resolvedBy.getOrElse(-1))

  // StatEntry
  val UserId                =   new IntKey[StatEntry[_]](_.userId, _.userId.getOrElse(-1))

  // Notification
  val Read                  =   new BooleanKey[Notification](_.read, _.isRead)

  // Review
  val Comment               =   new StringKey[Review](_.comment, _.message)
  val EndedAt               =   new TimestampKey[Review](_.endedAt, _.endedAt.get)

  // VisibilityChange
  val ResolvedByVC          =   new IntKey[VisibilityChange](_.resolvedBy, _.resolvedBy.get)
  val ResolvedAtVC          =   new TimestampKey[VisibilityChange](_.resolvedAt, _.resolvedAt.get)
}
