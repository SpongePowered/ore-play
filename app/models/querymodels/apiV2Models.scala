package models.querymodels
import java.time.LocalDateTime

import play.api.libs.json.Json.{obj, toJson}
import play.api.libs.json._

import models.project.{ReviewState, TagColor, Visibility}
import ore.permission.role.Role
import ore.project.Category

object APIV2Protocol {
  implicit val roleWrites: Writes[Role] = (role: Role) =>
    obj(
      "name"  -> role.value,
      "title" -> role.title,
      "color" -> role.color.hex
  )
}

case class APIV2Project(
    createdAt: LocalDateTime,
    pluginId: String,
    name: String,
    namespace: ProjectNamespace,
    recommendedVersion: Option[String],
    recommendedVersionTags: Option[List[APIV2VersionTag]],
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    description: Option[String],
    lastUpdated: LocalDateTime,
    visibility: Visibility,
    userStarred: Boolean,
    userWatching: Boolean,
    homepage: Option[String],
    issues: Option[String],
    source: Option[String],
    licenseName: Option[String],
    licenseUrl: Option[String],
    forumSync: Boolean
)
object APIV2Project {
  implicit val projectWrites: Writes[APIV2Project] = (project: APIV2Project) =>
    obj(
      "created_at" -> project.createdAt,
      "plugin_id"  -> project.pluginId,
      "name"       -> project.name,
      "namespace" -> obj(
        "owner_name" -> project.namespace.ownerName,
        "slug"       -> project.namespace.slug
      ),
      "recommended_version" -> obj(
        "name" -> project.recommendedVersion,
        "tags" -> project.recommendedVersionTags
      ),
      "views"        -> project.views,
      "downloads"    -> project.downloads,
      "stars"        -> project.stars,
      "category"     -> project.category.apiName,
      "description"  -> project.description,
      "last_updated" -> project.lastUpdated,
      "visibility" -> obj(
        "name"     -> project.visibility.nameKey,
        "modal"    -> project.visibility.showModal,
        "cssClass" -> project.visibility.cssClass
      ),
      "user_actions" -> obj(
        "starred"  -> project.userStarred,
        "watching" -> project.userWatching
      ),
      "settings" -> obj(
        "homepage" -> project.homepage,
        "issues"   -> project.issues,
        "source"   -> project.source,
        "license" -> obj(
          "name" -> project.licenseName,
          "url"  -> project.licenseUrl
        ),
        "forumSync" -> project.forumSync
      ),
  )
}

case class APIV2ProjectMember(
    user: String,
    roles: List[Role]
)
object APIV2ProjectMember {
  import APIV2Protocol._
  implicit val memberWrites: Writes[APIV2ProjectMember] = (member: APIV2ProjectMember) =>
    obj(
      "user"  -> member.user,
      "roles" -> toJson(member.roles)
  )
}

case class APIV2Version(
    createdAt: LocalDateTime,
    name: String,
    dependenciesIds: List[String],
    description: Option[String],
    downloads: Int,
    fileSize: Long,
    md5Hash: String,
    fileName: String,
    authorName: Option[String],
    reviewState: ReviewState,
    tags: List[APIV2VersionTag]
)
object APIV2Version {

  implicit val versionWrites: Writes[APIV2Version] = (version: APIV2Version) =>
    obj(
      "created_at" -> version.createdAt,
      "name"       -> version.name,
      "dependecies" -> version.dependenciesIds.map { depId =>
        val data = depId.split(":")
        obj(
          "plugin_id" -> data(0),
          "version"   -> data.lift(1)
        )
      },
      "description"  -> version.description,
      "downloads"    -> version.downloads,
      "file_size"    -> version.fileSize,
      "md5"          -> version.md5Hash,
      "file_name"    -> version.fileName,
      "author"       -> version.authorName,
      "review_state" -> version.reviewState.toString,
      "tags"         -> version.tags
  )
}

case class APIV2VersionTag(
    name: String,
    data: String,
    color: TagColor
)
object APIV2VersionTag {
  implicit val tagWrites: Writes[APIV2VersionTag] = (tag: APIV2VersionTag) =>
    obj(
      "name" -> tag.name,
      "data" -> tag.data,
      "color" -> obj(
        "background" -> tag.color.background,
        "foreground" -> tag.color.foreground
      )
  )
}

case class APIV2User(
    createdAt: LocalDateTime,
    name: String,
    tagline: Option[String],
    joinDate: Option[LocalDateTime],
    roles: List[Role]
)
object APIV2User {
  import APIV2Protocol._
  implicit val userWrites: Writes[APIV2User] = (user: APIV2User) =>
    obj(
      "created_at" -> user.createdAt,
      "name"       -> user.name,
      "tagline"    -> user.tagline,
      "join_date"  -> user.joinDate,
      "roles"      -> toJson(user.roles)
  )
}
