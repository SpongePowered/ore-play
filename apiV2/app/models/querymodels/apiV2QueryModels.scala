package models.querymodels

import java.time.LocalDateTime

import play.api.mvc.RequestHeader

import ore.models.project.{ReviewState, TagColor, Visibility}
import models.protocols.APIV2
import ore.OreConfig
import ore.data.project.{Category, ProjectNamespace}
import ore.models.project.io.ProjectFiles
import ore.models.user.User
import ore.permission.role.Role
import ore.util.OreMDC
import util.syntax._

import cats.syntax.all._
import cats.instances.option._

case class APIV2QueryProject(
    createdAt: LocalDateTime,
    pluginId: String,
    name: String,
    namespace: ProjectNamespace,
    recommendedVersion: Option[String],
    recommendedVersionTags: Option[List[APIV2QueryVersionTag]],
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
    sources: Option[String],
    support: Option[String],
    licenseName: Option[String],
    licenseUrl: Option[String],
    forumSync: Boolean
) {

  def asProtocol(
      implicit projectFiles: ProjectFiles,
      requestHeader: RequestHeader,
      mdc: OreMDC,
      config: OreConfig
  ): APIV2.Project = {
    val iconPath = projectFiles.getIconPath(namespace.ownerName, name)
    val iconUrl =
      if (iconPath.isDefined)
        controllers.project.routes.Projects.showIcon(namespace.ownerName, namespace.slug).absoluteURL()
      else User.avatarUrl(namespace.ownerName)

    APIV2.Project(
      createdAt,
      pluginId,
      name,
      APIV2.ProjectNamespace(
        namespace.ownerName,
        namespace.slug
      ),
      (recommendedVersion, recommendedVersionTags).mapN { (version, tags) =>
        APIV2.RecommendedVersion(
          version,
          tags.map(_.asProtocol)
        )
      },
      APIV2.ProjectStats(
        views,
        downloads,
        stars
      ),
      category,
      description,
      lastUpdated,
      visibility,
      APIV2.UserActions(
        userStarred,
        userWatching
      ),
      APIV2.ProjectSettings(
        homepage,
        issues,
        sources,
        support,
        APIV2.ProjectLicense(licenseName, licenseUrl),
        forumSync
      ),
      iconUrl
    )
  }
}

case class APIV2QueryCompactProject(
    pluginId: String,
    name: String,
    namespace: ProjectNamespace,
    recommendedVersion: Option[String],
    recommendedVersionTags: Option[List[APIV2QueryVersionTag]],
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    visibility: Visibility
) {
  def asProtocol: APIV2.CompactProject = APIV2.CompactProject(
    pluginId,
    name,
    APIV2.ProjectNamespace(
      namespace.ownerName,
      namespace.slug
    ),
    (recommendedVersion, recommendedVersionTags).mapN { (version, tags) =>
      APIV2.RecommendedVersion(
        version,
        tags.map(_.asProtocol)
      )
    },
    APIV2.ProjectStats(
      views,
      downloads,
      stars
    ),
    category,
    visibility,
  )
}

case class APIV2QueryProjectMember(
    user: String,
    roles: List[Role]
) {

  def asProtocol: APIV2.ProjectMember = APIV2.ProjectMember(
    user,
    roles.map { role =>
      APIV2.Role(
        role.value,
        role.title,
        role.color.hex
      )
    }
  )
}

case class APIV2QueryVersion(
    createdAt: LocalDateTime,
    name: String,
    dependenciesIds: List[String],
    visibility: Visibility,
    description: Option[String],
    downloads: Long,
    fileSize: Long,
    md5Hash: String,
    fileName: String,
    authorName: Option[String],
    reviewState: ReviewState,
    tags: List[APIV2QueryVersionTag]
) {

  def asProtocol: APIV2.Version = APIV2.Version(
    createdAt,
    name,
    dependenciesIds.map { depId =>
      val data = depId.split(":")
      APIV2.VersionDependency(
        data(0),
        data.lift(1)
      )
    },
    visibility,
    description,
    APIV2.VersionStats(downloads),
    APIV2.FileInfo(name, fileSize, md5Hash),
    authorName,
    reviewState,
    tags.map(_.asProtocol)
  )
}

case class APIV2QueryVersionTag(
    name: String,
    data: String,
    color: TagColor
) {

  def asProtocol: APIV2.VersionTag = APIV2.VersionTag(
    name,
    Some(data).filter(_.nonEmpty).filter(_ != "null"),
    APIV2.VersionTagColor(
      color.foreground,
      color.background
    )
  )
}

case class APIV2QueryUser(
    createdAt: LocalDateTime,
    name: String,
    tagline: Option[String],
    joinDate: Option[LocalDateTime],
    roles: List[Role]
) {

  def asProtocol: APIV2.User = APIV2.User(
    createdAt,
    name,
    tagline,
    joinDate,
    roles.map { role =>
      APIV2.Role(
        role.value,
        role.title,
        role.color.hex
      )
    }
  )
}
