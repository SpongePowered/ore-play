package models.protocols

import java.time.LocalDateTime

import models.project.{ReviewState, Visibility}
import ore.project.Category

import io.circe._
import io.circe.generic.extras._
import io.circe.syntax._
import shapeless.Typeable
import enumeratum.values._
import enumeratum._

object APIV2 {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  def optionToResult[A](s: String, opt: String => Option[A], history: List[CursorOp])(
      implicit tpe: Typeable[A]
  ): Either[DecodingFailure, A] =
    opt(s).toRight(DecodingFailure(s"$s is not a valid ${tpe.describe}", history))

  def valueEnumEncoder[V, A <: ValueEnumEntry[V]](enumObj: ValueEnum[V, A])(name: A => String): Encoder[A] =
    (a: A) => name(a).asJson

  def valueEnumDecoder[V, A <: ValueEnumEntry[V]: Typeable](enumObj: ValueEnum[V, A])(name: A => String): Decoder[A] =
    (c: HCursor) => c.as[String].flatMap(optionToResult(_, s => enumObj.values.find(a => name(a) == s), c.history))

  def enumEncoder[A <: EnumEntry](enumObj: Enum[A])(name: A => String): Encoder[A] =
    (a: A) => name(a).asJson

  def enumDecoder[A <: EnumEntry: Typeable](enumObj: Enum[A])(name: A => String): Decoder[A] =
    (c: HCursor) => c.as[String].flatMap(optionToResult(_, s => enumObj.values.find(a => name(a) == s), c.history))

  implicit val visibilityDecoder: Decoder[Visibility] = valueEnumDecoder(Visibility)(_.nameKey)
  implicit val visibilityEncoder: Encoder[Visibility] = valueEnumEncoder(Visibility)(_.nameKey)

  implicit val categoryDecoder: Decoder[Category] = valueEnumDecoder(Category)(_.apiName)
  implicit val categoryEncoder: Encoder[Category] = valueEnumEncoder(Category)(_.apiName)

  implicit val reviewStateDecoder: Decoder[ReviewState] = valueEnumDecoder(ReviewState)(_.apiName)
  implicit val reviewStateEncoder: Encoder[ReviewState] = valueEnumEncoder(ReviewState)(_.apiName)

  //Project
  @ConfiguredJsonCodec case class Project(
      createdAt: LocalDateTime,
      pluginId: String,
      name: String,
      namespace: ProjectNamespace,
      recommendedVersion: Option[RecommendedVersion],
      stats: ProjectStats,
      category: Category,
      description: Option[String],
      lastUpdated: LocalDateTime,
      visibility: Visibility,
      userActions: UserActions,
      settings: ProjectSettings
  )

  @ConfiguredJsonCodec case class ProjectNamespace(owner: String, slug: String)
  @ConfiguredJsonCodec case class RecommendedVersion(version: String, tags: List[VersionTag])
  @ConfiguredJsonCodec case class VersionTag(name: String, data: Option[String], color: VersionTagColor)
  @ConfiguredJsonCodec case class VersionTagColor(foreground: String, background: String)
  @ConfiguredJsonCodec case class ProjectStats(views: Long, downloads: Long, stars: Long)
  @ConfiguredJsonCodec case class UserActions(starred: Boolean, watching: Boolean)
  @ConfiguredJsonCodec case class ProjectSettings(
      issues: Option[String],
      sources: Option[String],
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
      createdAt: LocalDateTime,
      name: String,
      dependencies: List[VersionDependency],
      visibility: Visibility,
      description: Option[String],
      stats: VersionStats,
      fileInfo: FileInfo,
      author: Option[String],
      reviewState: ReviewState,
      tags: List[VersionTag]
  )

  @ConfiguredJsonCodec case class VersionDependency(pluginId: String, version: Option[String])
  @ConfiguredJsonCodec case class VersionStats(downloads: Long)
  @ConfiguredJsonCodec case class FileInfo(name: String, sizeBytes: Long, md5Hash: String)

  //User
  @ConfiguredJsonCodec case class User(
      createdAt: LocalDateTime,
      name: String,
      tagline: Option[String],
      joinDate: Option[LocalDateTime],
      roles: List[Role]
  )
}
