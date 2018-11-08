package models.user

import scala.collection.immutable
import scala.concurrent.Future

import controllers.sugar.Requests.AuthRequest
import db.impl.schema.LoggedActionTable
import db.{Model, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.StatTracker
import ore.user.UserOwned

import com.github.tminglei.slickpg.InetString
import enumeratum.values.{IntEnum, IntEnumEntry}

case class LoggedActionModel(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    address: InetString,
    action: LoggedAction,
    actionContext: LoggedActionContext,
    actionContextId: ObjectReference,
    newState: String,
    oldState: String
) extends Model {

  override type T = LoggedActionTable
  override type M = LoggedActionModel

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): LoggedActionModel = this.copy(createdAt = theTime)
}
object LoggedActionModel {
  implicit val isUserOwned: UserOwned[LoggedActionModel] = (a: LoggedActionModel) => a.userId
}

sealed abstract class LoggedActionContext(val value: Int) extends IntEnumEntry

object LoggedActionContext extends IntEnum[LoggedActionContext] {

  case object Project      extends LoggedActionContext(0)
  case object Version      extends LoggedActionContext(1)
  case object ProjectPage  extends LoggedActionContext(2)
  case object User         extends LoggedActionContext(3)
  case object Organization extends LoggedActionContext(4)

  val values: immutable.IndexedSeq[LoggedActionContext] = findValues

}

sealed abstract class LoggedAction(
    val value: Int,
    val name: String,
    val context: LoggedActionContext,
    val description: String
) extends IntEnumEntry

case object LoggedAction extends IntEnum[LoggedAction] {

  case object ProjectVisibilityChange
      extends LoggedAction(
        0,
        "ProjectVisibilityChange",
        LoggedActionContext.Project,
        "The project visibility state was changed"
      )
  case object ProjectCreated
      extends LoggedAction(1, "ProjectCreated", LoggedActionContext.Project, "The project was created")
  case object ProjectRenamed
      extends LoggedAction(2, "ProjectRename", LoggedActionContext.Project, "The project was renamed")
  case object ProjectFlagged
      extends LoggedAction(3, "ProjectFlagged", LoggedActionContext.Project, "The project got flagged")
  case object ProjectSettingsChanged
      extends LoggedAction(
        4,
        "ProjectSettingsChanged",
        LoggedActionContext.Project,
        "The project settings were changed"
      )
  case object ProjectMemberRemoved
      extends LoggedAction(
        5,
        "ProjectMemberRemoved",
        LoggedActionContext.Project,
        "A Member was removed from the project"
      )
  case object ProjectIconChanged
      extends LoggedAction(6, "ProjectIconChanged", LoggedActionContext.Project, "The project icon was changed")
  case object ProjectPageEdited
      extends LoggedAction(7, "ProjectPageEdited", LoggedActionContext.ProjectPage, "A project page got edited")
  case object ProjectFlagResolved
      extends LoggedAction(13, "ProjectFlagResolved", LoggedActionContext.Project, "The flag was resolved")

  case object VersionDeleted
      extends LoggedAction(8, "VersionDeleted", LoggedActionContext.Version, "The version was deleted")
  case object VersionUploaded
      extends LoggedAction(9, "VersionUploaded", LoggedActionContext.Version, "A new version was uploaded")
  case object VersionApproved
      extends LoggedAction(10, "VersionApproved", LoggedActionContext.Version, "The version was approved")
  case object VersionAsRecommended
      extends LoggedAction(
        11,
        "VersionAsRecommended",
        LoggedActionContext.Version,
        "The version was set as recommended version"
      )
  case object VersionDescriptionEdited
      extends LoggedAction(
        12,
        "VersionDescriptionEdited",
        LoggedActionContext.Version,
        "The version description was edited"
      )
  case object VersionNonReviewChanged
      extends LoggedAction(
        17,
        "VersionNonReviewChanged",
        LoggedActionContext.Version,
        "If the review queue skip was changed"
      )

  case object UserTaglineChanged
      extends LoggedAction(14, "UserTaglineChanged", LoggedActionContext.User, "The user tagline changed")
  case object UserPgpKeySaved
      extends LoggedAction(15, "UserPgpKeySaved", LoggedActionContext.User, "The user saved a PGP Public Key")
  case object UserPgpKeyRemoved
      extends LoggedAction(16, "UserPgpKeyRemoved", LoggedActionContext.User, "The user removed a PGP Public Key")
  val values: immutable.IndexedSeq[LoggedAction] = findValues
}

object UserActionLogger {

  def log(
      request: AuthRequest[_],
      action: LoggedAction,
      actionContextId: ObjectReference,
      newState: String,
      oldState: String
  )(implicit service: ModelService): Future[LoggedActionModel] =
    service.insert(
      LoggedActionModel(
        ObjectId.Uninitialized,
        ObjectTimestamp.Uninitialized,
        request.user.id.value,
        InetString(StatTracker.remoteAddress(request)),
        action,
        action.context,
        actionContextId,
        newState,
        oldState
      )
    )

}
