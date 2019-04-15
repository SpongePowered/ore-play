package db.impl

import java.sql.{PreparedStatement, ResultSet}

import play.api.i18n.Lang

import models.project.{ReviewState, TagColor, Visibility}
import models.user.{LoggedAction, LoggedActionContext}
import ore.Color
import ore.db.OreProfile
import ore.permission.Permission
import ore.permission.role.{Role, RoleCategory}
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
import org.postgresql.util.PGobject
import slick.jdbc.JdbcType

/**
  * Custom Postgres driver to support array data and custom type mappings.
  */
trait OrePostgresDriver
    extends OreProfile
    with ExPostgresProfile
    with PgArraySupport
    with PgAggFuncSupport
    with PgWindowFuncSupport
    with PgNetSupport
    with PgPlayJsonSupport
    with PgEnumSupport
    with SlickValueEnumSupport { self =>

  override val api: OreDriver.type = OreDriver

  def pgjson = "jsonb"

  object OreDriver extends API with ArrayImplicits with NetImplicits with JsonImplicits {
    type ModelTable[M]          = self.ModelTable[M]
    type AssociativeTable[A, B] = self.AssociativeTable[A, B]
    type ModelFilter[T]         = self.ModelFilter[T]
    val ModelFilter: self.ModelFilter.type = self.ModelFilter

    implicit val colorTypeMapper: BaseColumnType[Color]           = mappedColumnTypeForValueEnum(Color)
    implicit val tagColorTypeMapper: BaseColumnType[TagColor]     = mappedColumnTypeForValueEnum(TagColor)
    implicit val roleTypeTypeMapper: BaseColumnType[Role]         = mappedColumnTypeForValueEnum(Role)
    implicit val categoryTypeMapper: BaseColumnType[Category]     = mappedColumnTypeForValueEnum(Category)
    implicit val flagReasonTypeMapper: BaseColumnType[FlagReason] = mappedColumnTypeForValueEnum(FlagReason)
    implicit val notificationTypeTypeMapper: BaseColumnType[NotificationType] =
      mappedColumnTypeForValueEnum(NotificationType)
    implicit val promptTypeMapper: BaseColumnType[Prompt]             = mappedColumnTypeForValueEnum(Prompt)
    implicit val downloadTypeTypeMapper: BaseColumnType[DownloadType] = mappedColumnTypeForValueEnum(DownloadType)
    implicit val projectApiKeyTypeTypeMapper: BaseColumnType[ProjectApiKeyType] =
      mappedColumnTypeForValueEnum(ProjectApiKeyType)
    implicit val visibilityTypeMapper: BaseColumnType[Visibility] = mappedColumnTypeForValueEnum(Visibility)
    implicit def loggedActionMapper[Ctx]: BaseColumnType[LoggedAction[Ctx]] =
      mappedColumnTypeForValueEnum(LoggedAction).asInstanceOf[BaseColumnType[LoggedAction[Ctx]]] // scalafix:ok
    implicit def loggedActionContextMapper[Ctx]: BaseColumnType[LoggedActionContext[Ctx]] =
      mappedColumnTypeForValueEnum(LoggedActionContext)
        .asInstanceOf[BaseColumnType[LoggedActionContext[Ctx]]] // scalafix:ok
    implicit val reviewStateTypeMapper: BaseColumnType[ReviewState] = mappedColumnTypeForValueEnum(ReviewState)

    implicit val langTypeMapper: BaseColumnType[Lang] =
      MappedJdbcType.base[Lang, String](_.toLocale.toLanguageTag, Lang.apply)

    implicit val permissionTypeMapper: BaseColumnType[Permission] = new DriverJdbcType[Permission] {
      override def sqlType: Int = java.sql.Types.BIT

      override def setValue(v: Permission, p: PreparedStatement, idx: Int): Unit = {
        val obj = new PGobject
        obj.setType("bit")
        obj.setValue(v.toBinString)
        p.setObject(idx, obj)
      }

      override def getValue(r: ResultSet, idx: Int): Permission = {
        val str = r.getString(idx)
        if (str == null) null.asInstanceOf[Permission]
        else Permission.fromBinString(str).get
      }

      override def updateValue(v: Permission, r: ResultSet, idx: Int): Unit = {
        val obj = new PGobject
        obj.setType("bit")
        obj.setValue(v.toBinString)
        r.updateObject(idx, obj)
      }

      override def hasLiteralForm: Boolean = false
    }

    /*
    implicit def dbRefBaseType[A]: BaseColumnType[DbRef[A]] = longColumnType.asInstanceOf[BaseColumnType[DbRef[A]]]
    implicit def dbRefArrayBaseType[A]: BaseColumnType[List[DbRef[A]]] =
      simpleLongListTypeMapper.asInstanceOf[BaseColumnType[List[DbRef[A]]]]
     */

    implicit val roleTypeListTypeMapper: DriverJdbcType[List[Role]] = new AdvancedArrayJdbcType[Role](
      "varchar",
      str => utils.SimpleArrayUtils.fromString[Role](s => Role.withValue(s))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[Role](_.value)(value)
    ).to(_.toList)

    implicit val promptListTypeMapper: DriverJdbcType[List[Prompt]] = new AdvancedArrayJdbcType[Prompt](
      "int2",
      str => utils.SimpleArrayUtils.fromString[Prompt](s => Prompt.withValue(Integer.parseInt(s)))(str).orNull,
      value => utils.SimpleArrayUtils.mkString[Prompt](_.value.toString)(value)
    ).to(_.toList)

    implicit val roleCategoryTypeMapper: JdbcType[RoleCategory] = createEnumJdbcType[RoleCategory](
      sqlEnumTypeName = "ROLE_CATEGORY",
      enumToString = {
        case RoleCategory.Global       => "global"
        case RoleCategory.Project      => "project"
        case RoleCategory.Organization => "organization"
      },
      stringToEnum = {
        case "global"       => RoleCategory.Global
        case "project"      => RoleCategory.Project
        case "organization" => RoleCategory.Organization
      },
      quoteName = false
    )

    implicit def nelArrayMapper[A](
        implicit base: BaseColumnType[List[A]]
    ): BaseColumnType[NEL[A]] = MappedJdbcType.base[NEL[A], List[A]](_.toList, NEL.fromListUnsafe)

    val WindowFunctions: WindowFunctions = new WindowFunctions {}
  }

}

object OrePostgresDriver extends OrePostgresDriver
