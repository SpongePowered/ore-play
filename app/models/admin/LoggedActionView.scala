package models.admin

import db.impl.schema.LoggedActionViewTable
import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import models.user.{LoggedAction, LoggedActionContext}
import ore.user.UserOwned

import com.github.tminglei.slickpg.InetString

case class LoggedProject(
    pId: Option[ObjectReference],
    pPluginId: Option[String],
    pSlug: Option[String],
    pOwnerName: Option[String]
)
case class LoggedProjectVersion(pvId: Option[ObjectReference], pvVersionString: Option[String])
case class LoggedProjectPage(ppId: Option[ObjectReference], ppSlug: Option[String])
case class LoggedSubject(sId: Option[ObjectReference], sName: Option[String])

case class LoggedActionViewModel(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    address: InetString,
    action: LoggedAction,
    actionContext: LoggedActionContext,
    actionContextId: ObjectReference,
    newState: String,
    oldState: String,
    uId: ObjectReference,
    uName: String,
    loggedProject: LoggedProject,
    loggedProjectVerison: LoggedProjectVersion,
    loggedProjectPage: LoggedProjectPage,
    loggedSubject: LoggedSubject,
    filterProject: Option[ObjectReference],
    filterVersion: Option[ObjectReference],
    filterPage: Option[ObjectReference],
    filterSubject: Option[ObjectReference],
    filterAction: Option[Int]
) extends Model {

  def contextId: ObjectReference      = actionContextId
  def actionType: LoggedActionContext = action.context
  def pId: Option[ObjectReference]    = loggedProject.pId
  def pPluginId: Option[String]       = loggedProject.pPluginId
  def pSlug: Option[String]           = loggedProject.pSlug
  def pOwnerName: Option[String]      = loggedProject.pOwnerName
  def pvId: Option[ObjectReference]   = loggedProjectVerison.pvId
  def pvVersionString: Option[String] = loggedProjectVerison.pvVersionString
  def ppId: Option[ObjectReference]   = loggedProjectPage.ppId
  def ppSlug: Option[String]          = loggedProjectPage.ppSlug
  def sId: Option[ObjectReference]    = loggedSubject.sId
  def sName: Option[String]           = loggedSubject.sName

  override type T = LoggedActionViewTable
  override type M = LoggedActionViewModel

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): LoggedActionViewModel = this.copy(createdAt = theTime)
}
object LoggedActionViewModel {
  implicit val isUserOwned: UserOwned[LoggedActionViewModel] = (a: LoggedActionViewModel) => a.userId
}
