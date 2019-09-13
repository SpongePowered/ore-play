package models.protocols

import java.time.LocalDateTime

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
      created_at: LocalDateTime,
      plugin_id: String,
      name: String,
      namespace: ProjectNamespace,
      promoted_versions: Seq[PromotedVersion],
      stats: ProjectStats,
      category: Category,
      description: Option[String],
      last_updated: LocalDateTime,
      visibility: Visibility,
      user_actions: UserActions,
      settings: ProjectSettings,
      icon_url: String
  )

  @ConfiguredJsonCodec case class CompactProject(
      plugin_id: String,
      name: String,
      namespace: ProjectNamespace,
      promoted_versions: Seq[PromotedVersion],
      stats: ProjectStats,
      category: Category,
      visibility: Visibility
  )

  @ConfiguredJsonCodec case class ProjectNamespace(owner: String, slug: String)
  @ConfiguredJsonCodec case class PromotedVersion(version: String, tags: Seq[PromotedVersionTag])
  @ConfiguredJsonCodec case class PromotedVersionTag(
      name: String,
      data: Option[String],
      display_data: Option[String],
      minecraft_version: Option[String],
      color: VersionTagColor
  )
  @ConfiguredJsonCodec case class VersionTag(name: String, data: Option[String], color: VersionTagColor)
  @ConfiguredJsonCodec case class VersionTagColor(foreground: String, background: String)
  @ConfiguredJsonCodec case class ProjectStats(views: Long, downloads: Long, stars: Long)
  @ConfiguredJsonCodec case class UserActions(starred: Boolean, watching: Boolean)
  @ConfiguredJsonCodec case class ProjectSettings(
      homepage: Option[String],
      issues: Option[String],
      sources: Option[String],
      support: Option[String],
      license: ProjectLicense,
      forum_sync: Boolean
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
      created_at: LocalDateTime,
      name: String,
      dependencies: List[VersionDependency],
      visibility: Visibility,
      description: Option[String],
      stats: VersionStats,
      file_info: FileInfo,
      author: Option[String],
      review_state: ReviewState,
      tags: List[VersionTag]
  )

  @ConfiguredJsonCodec case class VersionDependency(plugin_id: String, version: Option[String])
  @ConfiguredJsonCodec case class VersionStats(downloads: Long)
  @ConfiguredJsonCodec case class FileInfo(name: String, size_bytes: Long, md5_hash: String)

  //User
  @ConfiguredJsonCodec case class User(
      created_at: LocalDateTime,
      name: String,
      tagline: Option[String],
      join_date: Option[LocalDateTime],
      roles: List[Role]
  )
}
