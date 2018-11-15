package db.query
import java.net.InetAddress
import java.sql.Timestamp

import scala.reflect.runtime.universe.TypeTag

import play.api.i18n.Lang

import db.{ObjectId, ObjectReference, ObjectTimestamp}
import models.project.{ReviewState, TagColor, Visibility}
import models.user.{LoggedAction, LoggedActionContext}
import ore.Color
import ore.permission.role.{Role, RoleCategory, Trust}
import ore.project.io.DownloadType
import ore.project.{Category, FlagReason}
import ore.rest.ProjectApiKeyType
import ore.user.Prompt
import ore.user.notification.NotificationType

import cats.data.{NonEmptyList => NEL}
import com.github.tminglei.slickpg.InetString
import doobie._
import doobie.postgres.implicits._
import enumeratum.values.{ValueEnum, ValueEnumEntry}

trait DoobieOreProtocol {

  implicit val objectIdMeta: Meta[ObjectId]               = Meta[ObjectReference].xmap(ObjectId.apply, _.value)
  implicit val objectTimestampMeta: Meta[ObjectTimestamp] = Meta[Timestamp].xmap(ObjectTimestamp.apply, _.value)

  def enumeratumMeta[V: TypeTag, E <: ValueEnumEntry[V]: TypeTag](
      enum: ValueEnum[V, E]
  )(implicit meta: Meta[V]): Meta[E] =
    meta.xmap[E](enum.withValue, _.value)

  implicit val colorMeta: Meta[Color]                             = enumeratumMeta(Color)
  implicit val tagColorMeta: Meta[TagColor]                       = enumeratumMeta(TagColor)
  implicit val roleTypeMeta: Meta[Role]                           = enumeratumMeta(Role)
  implicit val categoryMeta: Meta[Category]                       = enumeratumMeta(Category)
  implicit val flagReasonMeta: Meta[FlagReason]                   = enumeratumMeta(FlagReason)
  implicit val notificationTypeMeta: Meta[NotificationType]       = enumeratumMeta(NotificationType)
  implicit val promptMeta: Meta[Prompt]                           = enumeratumMeta(Prompt)
  implicit val downloadTypeMeta: Meta[DownloadType]               = enumeratumMeta(DownloadType)
  implicit val pojectApiKeyTypeMeta: Meta[ProjectApiKeyType]      = enumeratumMeta(ProjectApiKeyType)
  implicit val visibilityMeta: Meta[Visibility]                   = enumeratumMeta(Visibility)
  implicit val loggedActionMeta: Meta[LoggedAction]               = enumeratumMeta(LoggedAction)
  implicit val loggedActionContextMeta: Meta[LoggedActionContext] = enumeratumMeta(LoggedActionContext)
  implicit val trustMeta: Meta[Trust]                             = enumeratumMeta(Trust)
  implicit val reviewStateMeta: Meta[ReviewState]                 = enumeratumMeta(ReviewState)

  implicit val langMeta: Meta[Lang] = Meta[String].xmap(Lang.apply, _.toLocale.toLanguageTag)
  implicit val inetStringMeta: Meta[InetString] =
    Meta[InetAddress].xmap(address => InetString(address.toString), str => InetAddress.getByName(str.value))

  implicit val roleCategoryMeta: Meta[RoleCategory] = pgEnumString[RoleCategory](
    name = "ROLE_CATEGORY",
    f = {
      case "global"       => RoleCategory.Global
      case "project"      => RoleCategory.Project
      case "organization" => RoleCategory.Organization
    },
    g = {
      case RoleCategory.Global       => "global"
      case RoleCategory.Project      => "project"
      case RoleCategory.Organization => "organization"
    }
  )

  implicit val promptArrayMeta: Meta[List[Prompt]] =
    Meta[List[Int]].xmap(_.map(Prompt.withValue), _.map(_.value))
  implicit val roleTypeArrayMeta: Meta[List[Role]] =
    Meta[List[String]].xmap(_.map(Role.withValue), _.map(_.value))

  implicit def unsafeNelMeta[A](implicit listMeta: Meta[List[A]], typeTag: TypeTag[NEL[A]]): Meta[NEL[A]] =
    listMeta.xmap(NEL.fromListUnsafe, _.toList)

}
object DoobieOreProtocol extends DoobieOreProtocol
