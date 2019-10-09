package ore.models.user

import scala.language.higherKinds

import scala.collection.immutable

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema._
import ore.db.{DbRef, ModelQuery}
import ore.models.organization.Organization
import ore.models.project.{Page, Project, Version}

import com.github.tminglei.slickpg.InetString
import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry}
import slick.lifted.TableQuery

case class LoggedActionCommon[Ctx](
    userId: Option[DbRef[User]],
    address: InetString,
    action: LoggedActionType[Ctx],
    ctxId: Option[DbRef[Ctx]],
    newState: String,
    oldState: String
)

case class LoggedActionProject(data: LoggedActionCommon[Project])
object LoggedActionProject
    extends DefaultModelCompanion[LoggedActionProject, LoggedActionProjectTable](TableQuery[LoggedActionProjectTable]) {
  implicit val query: ModelQuery[LoggedActionProject] = ModelQuery.from(this)
}

case class LoggedActionVersion(data: LoggedActionCommon[Version], projectId: Option[DbRef[Project]])
object LoggedActionVersion
    extends DefaultModelCompanion[LoggedActionVersion, LoggedActionVersionTable](TableQuery[LoggedActionVersionTable]) {
  implicit val query: ModelQuery[LoggedActionVersion] = ModelQuery.from(this)
}

case class LoggedActionPage(data: LoggedActionCommon[Page], projectId: Option[DbRef[Project]])
object LoggedActionPage
    extends DefaultModelCompanion[LoggedActionPage, LoggedActionPageTable](TableQuery[LoggedActionPageTable]) {
  implicit val query: ModelQuery[LoggedActionPage] = ModelQuery.from(this)
}

case class LoggedActionUser(data: LoggedActionCommon[User])
object LoggedActionUser
    extends DefaultModelCompanion[LoggedActionUser, LoggedActionUserTable](TableQuery[LoggedActionUserTable]) {
  implicit val query: ModelQuery[LoggedActionUser] = ModelQuery.from(this)
}

case class LoggedActionOrganization(data: LoggedActionCommon[Organization])
object LoggedActionOrganization
    extends DefaultModelCompanion[LoggedActionOrganization, LoggedActionOrganizationTable](
      TableQuery[LoggedActionOrganizationTable]
    ) {
  implicit val query: ModelQuery[LoggedActionOrganization] = ModelQuery.from(this)
}

sealed abstract class LoggedActionContext[Ctx](val value: Int) extends IntEnumEntry

object LoggedActionContext extends IntEnum[LoggedActionContext[_]] {

  case object Project      extends LoggedActionContext[ore.models.project.Project](0)
  case object Version      extends LoggedActionContext[ore.models.project.Version](1)
  case object ProjectPage  extends LoggedActionContext[ore.models.project.Page](2)
  case object User         extends LoggedActionContext[ore.models.user.User](3)
  case object Organization extends LoggedActionContext[Organization](4)

  val values: immutable.IndexedSeq[LoggedActionContext[_]] = findValues
}

sealed abstract class LoggedActionType[Ctx](
    val value: String,
    val name: String,
    val context: LoggedActionContext[Ctx],
    val description: String
) extends StringEnumEntry

case object LoggedActionType extends StringEnum[LoggedActionType[_]] {

  case object ProjectVisibilityChange
      extends LoggedActionType(
        "project_visibility_change",
        "ProjectVisibilityChange",
        LoggedActionContext.Project,
        "The project visibility state was changed"
      )
  case object ProjectRenamed
      extends LoggedActionType(
        "project_renamed",
        "ProjectRename",
        LoggedActionContext.Project,
        "The project was renamed"
      )
  case object ProjectFlagged
      extends LoggedActionType(
        "project_flagged",
        "ProjectFlagged",
        LoggedActionContext.Project,
        "The project got flagged"
      )
  case object ProjectSettingsChanged
      extends LoggedActionType(
        "project_settings_changed",
        "ProjectSettingsChanged",
        LoggedActionContext.Project,
        "The project settings were changed"
      )
  case object ProjectMemberRemoved
      extends LoggedActionType(
        "project_member_removed",
        "ProjectMemberRemoved",
        LoggedActionContext.Project,
        "A Member was removed from the project"
      )
  case object ProjectIconChanged
      extends LoggedActionType(
        "project_icon_changed",
        "ProjectIconChanged",
        LoggedActionContext.Project,
        "The project icon was changed"
      )
  case object ProjectPageEdited
      extends LoggedActionType(
        "project_page_edited",
        "ProjectPageEdited",
        LoggedActionContext.ProjectPage,
        "A project page got edited"
      )
  case object ProjectFlagResolved
      extends LoggedActionType(
        "project_flag_resolved",
        "ProjectFlagResolved",
        LoggedActionContext.Project,
        "The flag was resolved"
      )

  case object VersionDeleted
      extends LoggedActionType(
        "version_deleted",
        "VersionDeleted",
        LoggedActionContext.Version,
        "The version was deleted"
      )
  case object VersionUploaded
      extends LoggedActionType(
        "version_uploaded",
        "VersionUploaded",
        LoggedActionContext.Version,
        "A new version was uploaded"
      )
  case object VersionDescriptionEdited
      extends LoggedActionType(
        "version_description_changed",
        "VersionDescriptionEdited",
        LoggedActionContext.Version,
        "The version description was edited"
      )
  case object VersionReviewStateChanged
      extends LoggedActionType(
        "version_review_state_changed",
        "VersionReviewStateChanged",
        LoggedActionContext.Version,
        "If the review state changed"
      )

  case object UserTaglineChanged
      extends LoggedActionType(
        "user_tagline_changed",
        "UserTaglineChanged",
        LoggedActionContext.User,
        "The user tagline changed"
      )
  val values: immutable.IndexedSeq[LoggedActionType[_]] = findValues
}
