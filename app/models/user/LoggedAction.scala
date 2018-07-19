package models.user

import java.sql.Timestamp

import scala.collection.immutable

import com.github.tminglei.slickpg.InetString
import controllers.sugar.Requests.AuthRequest
import db.ModelService
import db.impl.LoggedActionTable
import db.impl.model.OreModel
import enumeratum.values.{IntEnum, IntEnumEntry}
import ore.StatTracker
import ore.user.UserOwned

import scala.concurrent.ExecutionContext

case class LoggedActionModel(override val id: Option[Int] = None,
                             override val createdAt: Option[Timestamp] = None,
                             private val _userId: Int,
                             private val _address: InetString,
                             private val _action: LoggedAction,
                             private val _actionContext: LoggedActionContext,
                             private val _actionContextId: Int,
                             private val _newState: String,
                             private val _oldState: String) extends OreModel(id, createdAt) with UserOwned {

  override type T = LoggedActionTable
  override type M = LoggedActionModel

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): LoggedActionModel = this.copy(createdAt = theTime)
  override def userId: Int = _userId

  def address: InetString = _address
  def action: LoggedAction = _action
  def oldState: String = _oldState
  def newState: String = _newState
  def contextId: Int = _actionContextId
  def actionType: LoggedActionContext = _action.context
}

sealed abstract class LoggedActionContext(val value: Int) extends IntEnumEntry

object LoggedActionContext extends IntEnum[LoggedActionContext] {

  case object Project     extends LoggedActionContext(0)
  case object Version     extends LoggedActionContext(1)
  case object ProjectPage extends LoggedActionContext(2)

  val values: immutable.IndexedSeq[LoggedActionContext] = findValues

}

sealed abstract class LoggedAction(val value: Int, val name: String, val context: LoggedActionContext, val description: String) extends IntEnumEntry

case object LoggedAction extends IntEnum[LoggedAction] {

  case object ProjectVisibilityChange   extends LoggedAction(0, "ProjectVisibilityChange", LoggedActionContext.Project, "The project visibility state was changed")
  case object ProjectCreated            extends LoggedAction(1, "ProjectCreated", LoggedActionContext.Project, "The project was created")
  case object ProjectRenamed            extends LoggedAction(2, "ProjectRename", LoggedActionContext.Project, "The project was renamed")
  case object ProjectFlagged            extends LoggedAction(3, "ProjectFlagged", LoggedActionContext.Project, "The project got flagged")
  case object ProjectSettingsChanged    extends LoggedAction(4, "ProjectSettingsChanged", LoggedActionContext.Project, "The project settings were changed")
  case object ProjectMemberRemoved      extends LoggedAction(5, "ProjectMemberRemoved", LoggedActionContext.Project, "A Member was removed from the project")
  case object ProjectIconChanged        extends LoggedAction(6, "ProjectIconChanged", LoggedActionContext.Project, "The project icon was changed")
  case object ProjectPageEdited         extends LoggedAction(7, "ProjectPageEdited", LoggedActionContext.ProjectPage, "A project page got edited")

  case object VersionDeleted            extends LoggedAction(8, "VersionDeleted", LoggedActionContext.Version, "The version was deleted")
  case object VersionUploaded           extends LoggedAction(9, "VersionUploaded", LoggedActionContext.Version, "A new version was uploaded")
  case object VersionApproved           extends LoggedAction(10, "VersionApproved", LoggedActionContext.Version, "The version was approved")
  case object VersionAsRecommended      extends LoggedAction(11, "VersionAsRecommended", LoggedActionContext.Version, "The version was set as recommended version")
  case object VersionDescriptionEdited  extends LoggedAction(12, "VersionDescriptionEdited", LoggedActionContext.Version, "The version description was edited")

  val values: immutable.IndexedSeq[LoggedAction] = findValues

}

object UserActionLogger {

  def log(request: AuthRequest[_], action: LoggedAction, actionContextId: Int, newState: String, oldState: String)
         (implicit service: ModelService, ex: ExecutionContext){

    service.insert(LoggedActionModel(None, None, request.user.userId, InetString(StatTracker.remoteAddress(request.request)), action, action.context, actionContextId, newState, oldState))
  }

}
