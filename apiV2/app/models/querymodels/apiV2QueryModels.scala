package models.querymodels

import java.time.LocalDateTime

import play.api.mvc.RequestHeader

import models.protocols.APIV2
import ore.OreConfig
import ore.data.project.{Category, ProjectNamespace}
import ore.models.project.io.ProjectFiles
import ore.models.project.{ReviewState, TagColor, Visibility}
import ore.models.user.User
import ore.permission.role.Role
import util.syntax._

import cats.instances.either._
import cats.instances.vector._
import cats.syntax.all._
import io.circe.{DecodingFailure, Json}
import zio.ZIO
import zio.blocking.Blocking

case class APIV2QueryProject(
    createdAt: LocalDateTime,
    pluginId: String,
    name: String,
    namespace: ProjectNamespace,
    promotedVersions: Json,
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
      implicit projectFiles: ProjectFiles[ZIO[Blocking, Nothing, ?]],
      requestHeader: RequestHeader,
      config: OreConfig
  ): ZIO[Blocking, Nothing, APIV2.Project] = {
    val iconPath = projectFiles.getIconPath(namespace.ownerName, name)
    val iconUrlF = iconPath.map(_.isDefined).map {
      case true  => controllers.project.routes.Projects.showIcon(namespace.ownerName, namespace.slug).absoluteURL()
      case false => User.avatarUrl(namespace.ownerName)
    }

    for {
      promotedVersionDecoded <- ZIO.fromEither(APIV2QueryProject.decodePromotedVersions(promotedVersions)).orDie
      iconUrl                <- iconUrlF
    } yield {
      APIV2.Project(
        createdAt,
        pluginId,
        name,
        APIV2.ProjectNamespace(
          namespace.ownerName,
          namespace.slug
        ),
        promotedVersionDecoded,
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
}
object APIV2QueryProject {
  def decodePromotedVersions(promotedVersions: Json): Either[DecodingFailure, Vector[APIV2.PromotedVersion]] =
    for {
      jsons <- promotedVersions.hcursor.values.toRight(DecodingFailure("Invalid promoted versions", Nil))
      res <- jsons.toVector.traverse { json =>
        val cursor = json.hcursor

        for {
          version <- cursor.get[String]("version_string")
          tagName <- cursor.get[String]("tag_name")
          data    <- cursor.get[Option[String]]("tag_version")
          color <- cursor
            .get[Int]("tag_color")
            .flatMap { i =>
              TagColor
                .withValueOpt(i)
                .toRight(DecodingFailure(s"Invalid TagColor $i", cursor.downField("tag_color").history))
            }
        } yield APIV2.PromotedVersion(
          version,
          APIV2.VersionTag(
            tagName,
            data,
            APIV2.VersionTagColor(
              color.foreground,
              color.background
            )
          )
        )
      }
    } yield res
}

case class APIV2QueryCompactProject(
    pluginId: String,
    name: String,
    namespace: ProjectNamespace,
    promotedVersions: Json,
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    visibility: Visibility
) {
  def asProtocol: Either[DecodingFailure, APIV2.CompactProject] =
    APIV2QueryProject.decodePromotedVersions(promotedVersions).map { decodedPromotedVersions =>
      APIV2.CompactProject(
        pluginId,
        name,
        APIV2.ProjectNamespace(
          namespace.ownerName,
          namespace.slug
        ),
        decodedPromotedVersions,
        APIV2.ProjectStats(
          views,
          downloads,
          stars
        ),
        category,
        visibility
      )
    }
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
    data: Option[String],
    color: TagColor
) {

  def asProtocol: APIV2.VersionTag = APIV2.VersionTag(
    name,
    data,
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
