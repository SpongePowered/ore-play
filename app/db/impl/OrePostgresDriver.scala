package db.impl

import play.api.i18n.Lang

import models.project.{TagColor, Visibility}
import models.user.{LoggedAction, LoggedActionContext}
import ore.Color
import ore.permission.role.RoleType
import ore.project.io.DownloadType
import ore.project.{Category, FlagReason}
import ore.rest.ProjectApiKeyType
import ore.user.Prompt
import ore.user.notification.NotificationType

import cats.data.{NonEmptyList => NEL}
import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import com.github.tminglei.slickpg.window.PgWindowFuncSupport
import enumeratum.values.SlickValueEnumSupport

/**
  * Custom Postgres driver to support array data and custom type mappings.
  */
trait OrePostgresDriver
    extends ExPostgresProfile
    with PgArraySupport
    with PgAggFuncSupport
    with PgWindowFuncSupport
    with PgNetSupport
    with PgJsonSupport
    with SlickValueEnumSupport {

  override val api: OreDriver.type = OreDriver

  def pgjson = "jsonb"

  object OreDriver extends API with ArrayImplicits with NetImplicits {
    implicit val colorTypeMapper: BaseColumnType[Color]           = mappedColumnTypeForValueEnum(Color)
    implicit val tagColorTypeMapper: BaseColumnType[TagColor]     = mappedColumnTypeForValueEnum(TagColor)
    implicit val roleTypeTypeMapper: BaseColumnType[RoleType]     = mappedColumnTypeForValueEnum(RoleType)
    implicit val categoryTypeMapper: BaseColumnType[Category]     = mappedColumnTypeForValueEnum(Category)
    implicit val flagReasonTypeMapper: BaseColumnType[FlagReason] = mappedColumnTypeForValueEnum(FlagReason)
    implicit val notificationTypeTypeMapper: BaseColumnType[NotificationType] =
      mappedColumnTypeForValueEnum(NotificationType)
    implicit val promptTypeMapper: BaseColumnType[Prompt]             = mappedColumnTypeForValueEnum(Prompt)
    implicit val downloadTypeTypeMapper: BaseColumnType[DownloadType] = mappedColumnTypeForValueEnum(DownloadType)
    implicit val projectApiKeyTypeTypeMapper: BaseColumnType[ProjectApiKeyType] =
      mappedColumnTypeForValueEnum(ProjectApiKeyType)
    implicit val visibilityTypeMapper: BaseColumnType[Visibility] = mappedColumnTypeForValueEnum(Visibility)
    implicit val loggedActionMapper: BaseColumnType[LoggedAction] = mappedColumnTypeForValueEnum(LoggedAction)
    implicit val loggedActionContextMapper: BaseColumnType[LoggedActionContext] =
      mappedColumnTypeForValueEnum(LoggedActionContext)

    implicit val langTypeMapper: BaseColumnType[Lang] =
      MappedJdbcType.base[Lang, String](_.toLocale.toLanguageTag, Lang.apply)

    /*
    implicit def dbRefBaseType[A]: BaseColumnType[DbRef[A]] = longColumnType.asInstanceOf[BaseColumnType[DbRef[A]]]
    implicit def dbRefArrayBaseType[A]: BaseColumnType[List[DbRef[A]]] =
      simpleLongListTypeMapper.asInstanceOf[BaseColumnType[List[DbRef[A]]]]
     */

    implicit val roleTypeListTypeMapper: DriverJdbcType[List[RoleType]] = new AdvancedArrayJdbcType[RoleType](
      "varchar",
      str => utils.SimpleArrayUtils.fromString[RoleType](s => RoleType.withValue(s))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[RoleType](_.value)(value)
    ).to(_.toList)

    implicit val promptListTypeMapper: DriverJdbcType[List[Prompt]] = new AdvancedArrayJdbcType[Prompt](
      "int2",
      str => utils.SimpleArrayUtils.fromString[Prompt](s => Prompt.withValue(Integer.parseInt(s)))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[Prompt](_.value.toString)(value)
    ).to(_.toList)

    implicit def nelArrayMapper[A](
        implicit base: BaseColumnType[List[A]]
    ): BaseColumnType[NEL[A]] = MappedJdbcType.base[NEL[A], List[A]](_.toList, NEL.fromListUnsafe)

    val WindowFunctions: WindowFunctions = new WindowFunctions {}
  }

}

object OrePostgresDriver extends OrePostgresDriver
