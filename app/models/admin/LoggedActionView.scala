package models.admin

import com.github.tminglei.slickpg.InetString

import db.ObjectReference

import db.{Model, ObjectId, ObjectTimestamp}
import db.impl.LoggedActionViewTable
import models.user.{LoggedAction, LoggedActionContext}
import ore.user.UserOwned

case class LoggedProject(pId: Option[Int], pPluginId: Option[String], pSlug: Option[String], pOwnerName: Option[String])
case class LoggedProjectVersion(pvId: Option[Int], pvVersionString: Option[String])
case class LoggedProjectPage(ppId: Option[Int], ppSlug: Option[String])
case class LoggedSubject(sId: Option[Int], sName: Option[String])

case class LoggedActionViewModel(id: ObjectId = ObjectId.Uninitialized,
                                 createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                                 userId: ObjectReference,
                                 address: InetString,
                                 action: LoggedAction,
                                 actionContext: LoggedActionContext,
                                 actionContextId: Int,
                                 newState: String,
                                 oldState: String,
                                 uId: Int,
                                 uName: String,
                                 loggedProject: LoggedProject,
                                 loggedProjectVerison: LoggedProjectVersion,
                                 loggedProjectPage: LoggedProjectPage,
                                 loggedSubject: LoggedSubject,
                                 filterProject: Option[Int],
                                 filterVersion: Option[Int],
                                 filterPage: Option[Int],
                                 filterSubject: Option[Int],
                                 filterAction: Option[Int]) extends Model with UserOwned {

  def contextId: Int = actionContextId
  def actionType: LoggedActionContext = action.context
  def pId: Option[Int] = loggedProject.pId
  def pPluginId: Option[String] = loggedProject.pPluginId
  def pSlug: Option[String] = loggedProject.pSlug
  def pOwnerName: Option[String] = loggedProject.pOwnerName
  def pvId: Option[Int] = loggedProjectVerison.pvId
  def pvVersionString: Option[String] = loggedProjectVerison.pvVersionString
  def ppId: Option[Int] = loggedProjectPage.ppId
  def ppSlug: Option[String] = loggedProjectPage.ppSlug
  def sId: Option[Int] = loggedSubject.sId
  def sName: Option[String] = loggedSubject.sName

  override type T = LoggedActionViewTable
  override type M = LoggedActionViewModel

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): LoggedActionViewModel = this.copy(createdAt = theTime)
}
