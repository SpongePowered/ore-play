package db.impl

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import db.table.key.Aliases
import enumeratum.values.SlickValueEnumSupport
import models.project.TagColors.TagColor
import models.project.VisibilityTypes.Visibility
import models.project.{TagColors, VisibilityTypes}
import models.user.{LoggedAction, LoggedActionContext}
import ore.Colors
import ore.Colors.Color
import ore.permission.role.RoleType
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import ore.project.io.DownloadTypes
import ore.project.io.DownloadTypes.DownloadType
import ore.project.{Categories, FlagReasons}
import ore.rest.ProjectApiKeyTypes
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType
import ore.user.Prompts
import ore.user.Prompts.Prompt
import ore.user.notification.NotificationTypes
import ore.user.notification.NotificationTypes.NotificationType
import play.api.i18n.Lang
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

/**
  * Custom Postgres driver to support array data and custom type mappings.
  */
trait OrePostgresDriver extends ExPostgresProfile with PgArraySupport with PgAggFuncSupport with PgNetSupport with SlickValueEnumSupport {

  override val api: OreDriver.type = OreDriver

  def pgjson = "jsonb"

  object OreDriver extends API with ArrayImplicits with NetImplicits with Aliases {
    implicit val colorTypeMapper            : JdbcType[Color] with BaseTypedType[Color]                             = MappedJdbcType.base[Color, Int](_.id, Colors.apply)
    implicit val tagColorTypeMapper         : JdbcType[TagColor] with BaseTypedType[TagColor]                       = MappedJdbcType.base[TagColor, Int](_.id, TagColors.apply)
    implicit val roleTypeTypeMapper         : JdbcType[RoleType] with BaseTypedType[RoleType]                       = mappedColumnTypeForValueEnum(RoleType)
    implicit val roleTypeListTypeMapper     : DriverJdbcType[List[RoleType]]                                        = new AdvancedArrayJdbcType[RoleType]("varchar",
      str => utils.SimpleArrayUtils.fromString[RoleType](s => RoleType.withValue(s))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[RoleType](_.value)(value)
    ).to(_.toList)
    implicit val categoryTypeMapper         : JdbcType[Category] with BaseTypedType[Category]                       = MappedJdbcType.base[Category, Int](_.id, Categories.apply)
    implicit val flagReasonTypeMapper       : JdbcType[FlagReason] with BaseTypedType[FlagReason]                   = MappedJdbcType.base[FlagReason, Int](_.id, FlagReasons.apply)
    implicit val notificationTypeTypeMapper : JdbcType[NotificationType] with BaseTypedType[NotificationType]       = MappedJdbcType.base[NotificationType, Int](_.id, NotificationTypes.apply)
    implicit val promptTypeMapper           : JdbcType[Prompt] with BaseTypedType[Prompt]                           = MappedJdbcType.base[Prompt, Int](_.id, Prompts.apply)
    implicit val promptListTypeMapper       : DriverJdbcType[List[Prompt]]                                          = new AdvancedArrayJdbcType[Prompt]("int2",
      str => utils.SimpleArrayUtils.fromString[Prompt](s => Prompts(Integer.parseInt(s)))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[Prompt](_.id.toString)(value)
    ).to(_.toList)
    implicit val downloadTypeTypeMapper     : JdbcType[DownloadType] with BaseTypedType[DownloadType]               = MappedJdbcType.base[DownloadType, Int](_.id, DownloadTypes.apply)
    implicit val projectApiKeyTypeTypeMapper: JdbcType[ProjectApiKeyType] with BaseTypedType[ProjectApiKeyType]     = MappedJdbcType.base[ProjectApiKeyType, Int](_.id, ProjectApiKeyTypes.apply)
    implicit val visibiltyTypeMapper        : JdbcType[Visibility] with BaseTypedType[Visibility]                   = MappedJdbcType.base[Visibility, Int](_.id, VisibilityTypes.withId)
    implicit val loggedActionMapper         : JdbcType[LoggedAction] with BaseTypedType[LoggedAction]               = MappedJdbcType.base[LoggedAction, Int](_.value, LoggedAction.withValue)
    implicit val loggedActionContextMapper  : JdbcType[LoggedActionContext] with BaseTypedType[LoggedActionContext] = MappedJdbcType.base[LoggedActionContext, Int](_.value, LoggedActionContext.withValue)
    implicit val langTypeMapper             : JdbcType[Lang] with BaseTypedType[Lang]                               = MappedJdbcType.base[Lang, String](_.toLocale.toLanguageTag, Lang.apply)
  }

}

object OrePostgresDriver extends OrePostgresDriver
