package models.protocols

import java.time.OffsetDateTime

import ore.data.project.Category
import ore.models.project.{ReviewState, Visibility}

import enumeratum._
import enumeratum.values._
import io.circe._
import io.circe.generic.extras._
import io.circe.syntax._
import shapeless.Typeable

object APIV2 {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  def optionToResult[A](s: String, opt: String => Option[A], history: List[CursorOp])(
      implicit tpe: Typeable[A]
  ): Either[DecodingFailure, A] =
    opt(s).toRight(DecodingFailure(s"$s is not a valid ${tpe.describe}", history))

  def valueEnumCodec[V, A <: ValueEnumEntry[V]: Typeable](enumObj: ValueEnum[V, A])(name: A => String): Codec[A] =
    Codec.from(
      (c: HCursor) => c.as[String].flatMap(optionToResult(_, s => enumObj.values.find(a => name(a) == s), c.history)),
      (a: A) => name(a).asJson
    )

  def enumCodec[A <: EnumEntry: Typeable](enumObj: Enum[A])(name: A => String): Codec[A] = Codec.from(
    (c: HCursor) => c.as[String].flatMap(optionToResult(_, s => enumObj.values.find(a => name(a) == s), c.history)),
    (a: A) => name(a).asJson
  )

  implicit val visibilityCodec: Codec[Visibility]   = valueEnumCodec(Visibility)(_.nameKey)
  implicit val categoryCodec: Codec[Category]       = valueEnumCodec(Category)(_.apiName)
  implicit val reviewStateCodec: Codec[ReviewState] = valueEnumCodec(ReviewState)(_.apiName)

  //Project
  @ConfiguredJsonCodec case class Project(
      createdAt: OffsetDateTime,
      pluginId: String,
      name: String,
      namespace: ProjectNamespace,
      promotedVersions: Seq[PromotedVersion],
      stats: ProjectStatsAll,
      category: Category,
      description: Option[String],
      lastUpdated: OffsetDateTime,
      visibility: Visibility,
      userActions: UserActions,
      settings: ProjectSettings,
      iconUrl: String
  )

  @ConfiguredJsonCodec case class CompactProject(
      pluginId: String,
      name: String,
      namespace: ProjectNamespace,
      promotedVersions: Seq[PromotedVersion],
      stats: ProjectStatsAll,
      category: Category,
      visibility: Visibility
  )

  @ConfiguredJsonCodec case class ProjectNamespace(owner: String, slug: String)
  @ConfiguredJsonCodec case class PromotedVersion(version: String, tags: Seq[PromotedVersionTag])
  @ConfiguredJsonCodec case class PromotedVersionTag(
      name: String,
      data: Option[String],
      displayData: Option[String],
      minecraftVersion: Option[String],
      color: VersionTagColor
  )
  @ConfiguredJsonCodec case class VersionTagColor(foreground: String, background: String)
  @ConfiguredJsonCodec case class ProjectStatsAll(
      views: Long,
      downloads: Long,
      recent_views: Long,
      recent_downloads: Long,
      stars: Long,
      watchers: Long
  )
  @ConfiguredJsonCodec case class UserActions(starred: Boolean, watching: Boolean)
  @ConfiguredJsonCodec case class ProjectSettings(
      homepage: Option[String],
      issues: Option[String],
      sources: Option[String],
      support: Option[String],
      license: ProjectLicense,
      forumSync: Boolean
  )
  @ConfiguredJsonCodec case class ProjectLicense(name: Option[String], url: Option[String])

  //Project member
  @ConfiguredJsonCodec case class ProjectMember(
      user: String,
      roles: List[Role]
  )

  @ConfiguredJsonCodec case class Role(
      name: String,
      title: String,
      color: String
  )

  //Version
  @ConfiguredJsonCodec case class Version(
      createdAt: OffsetDateTime,
      name: String,
      dependencies: List[VersionDependency],
      visibility: Visibility,
      stats: VersionStatsAll,
      fileInfo: FileInfo,
      author: Option[String],
      reviewState: ReviewState
  )

  @ConfiguredJsonCodec case class VersionDescription(description: String)

  @ConfiguredJsonCodec case class VersionDependency(plugin_id: String, version: Option[String])
  @ConfiguredJsonCodec case class VersionStatsAll(downloads: Long)
  @ConfiguredJsonCodec case class FileInfo(name: String, sizeBytes: Long, md5Hash: String)

  //User
  @ConfiguredJsonCodec case class User(
      createdAt: OffsetDateTime,
      name: String,
      tagline: Option[String],
      joinDate: Option[OffsetDateTime],
      roles: List[Role]
  )

  @ConfiguredJsonCodec case class ProjectStatsDay(
      downloads: Long,
      views: Long
  )

  @ConfiguredJsonCodec case class VersionStatsDay(
      downloads: Long
  )
}
