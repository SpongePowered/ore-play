package db.query
import java.net.InetAddress
import java.sql.Timestamp

import scala.reflect.runtime.universe.TypeTag

import play.api.i18n.Lang

import db.{ObjectId, ObjectReference, ObjectTimestamp}
import models.project.{TagColor, Visibility}
import models.querymodels.ViewTag
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
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import enumeratum.values.{ValueEnum, ValueEnumEntry}

trait DoobieOreProtocol {

  def createLogger(name: String): LogHandler = {
    val logger = play.api.Logger(name)

    LogHandler {
      case util.log.Success(sql, args, exec, processing) =>
        logger.info(
          s"""|Successful Statement Execution:
              |
              |  ${sql.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")}
              |
              | arguments = [${args.mkString(", ")}]
              |   elapsed = ${exec.toMillis} ms exec + ${processing.toMillis} ms processing (${(exec + processing).toMillis} ms total)""".stripMargin
        )
      case util.log.ProcessingFailure(sql, args, exec, processing, failure) =>
        logger.error(
          s"""|Failed Resultset Processing:
              |
              |  ${sql.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")}
              |
              | arguments = [${args.mkString(", ")}]
              |   elapsed = ${exec.toMillis} ms exec + ${processing.toMillis} ms processing (failed) (${(exec + processing).toMillis} ms total)
              |   failure = ${failure.getMessage}""".stripMargin,
          failure
        )
      case util.log.ExecFailure(sql, args, exec, failure) =>
        logger.error(
          s"""Failed Statement Execution:
             |
             |  ${sql.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")}
             |
             | arguments = [${args.mkString(", ")}]
             |   elapsed = ${exec.toMillis} ms exec (failed)
             |   failure = ${failure.getMessage}""".stripMargin,
          failure
        )
    }
  }

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

  implicit val tagColorArrayMeta: Meta[List[TagColor]] =
    Meta[List[Int]].xmap(_.map(TagColor.withValue), _.map(_.value))

  implicit def unsafeNelMeta[A](implicit listMeta: Meta[List[A]], typeTag: TypeTag[NEL[A]]): Meta[NEL[A]] =
    listMeta.xmap(NEL.fromListUnsafe, _.toList)

  implicit val viewTagListComposite: Composite[List[ViewTag]] =
    Composite[(List[String], List[String], List[TagColor])].imap(
      { case (name, data, color) => name.zip(data).zip(color).map(t => ViewTag(t._1._1, t._1._2, t._2)) }
    )(_.flatMap(ViewTag.unapply).unzip3)

}
object DoobieOreProtocol extends DoobieOreProtocol
