package db.impl

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import db.table.key.Aliases
import models.project.TagColors.TagColor
import models.project.VisibilityTypes.Visibility
import models.project.{TagColors, VisibilityTypes}
import ore.Colors
import ore.Colors.Color
import ore.permission.role.RoleTypes
import ore.permission.role.RoleTypes.RoleType
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

/**
  * Custom Postgres driver to support array data and custom type mappings.
  */
trait OrePostgresDriver extends ExPostgresProfile with PgArraySupport with PgAggFuncSupport with PgNetSupport {

  override val api = OreDriver

  def pgjson = "jsonb"

  object OreDriver extends API with ArrayImplicits with NetImplicits with Aliases {
    implicit val colorTypeMapper = MappedJdbcType.base[Color, Int](_.id, Colors.apply)
    implicit val tagColorTypeMapper = MappedJdbcType.base[TagColor, Int](_.id, TagColors.apply)
    implicit val roleTypeTypeMapper = MappedJdbcType.base[RoleType, Int](_.roleId, RoleTypes.withId)
    implicit val roleTypeListTypeMapper = new AdvancedArrayJdbcType[RoleType]("int2",
      str => utils.SimpleArrayUtils.fromString[RoleType](s => RoleTypes.withId(Integer.parseInt(s)))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[RoleType](_.roleId.toString)(value)
    ).to(_.toList)
    implicit val categoryTypeMapper = MappedJdbcType.base[Category, Int](_.id, Categories.apply)
    implicit val flagReasonTypeMapper = MappedJdbcType.base[FlagReason, Int](_.id, FlagReasons.apply)
    implicit val notificationTypeTypeMapper = MappedJdbcType.base[NotificationType, Int](_.id, NotificationTypes.apply)
    implicit val promptTypeMapper = MappedJdbcType.base[Prompt, Int](_.id, Prompts.apply)
    implicit val promptListTypeMapper = new AdvancedArrayJdbcType[Prompt]("int2",
      str => utils.SimpleArrayUtils.fromString[Prompt](s => Prompts(Integer.parseInt(s)))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[Prompt](_.id.toString)(value)
    ).to(_.toList)
    implicit val downloadTypeTypeMapper = MappedJdbcType.base[DownloadType, Int](_.id, DownloadTypes.apply)
    implicit val projectApiKeyTypeTypeMapper = MappedJdbcType.base[ProjectApiKeyType, Int](_.id, ProjectApiKeyTypes.apply)
    implicit val visibiltyTypeMapper = MappedJdbcType.base[Visibility, Int](_.id, VisibilityTypes.withId)
  }

}

object OrePostgresDriver extends OrePostgresDriver
