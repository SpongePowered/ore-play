package models.admin

import com.github.tminglei.slickpg.InetString

import db.{ObjectId, ObjectTimestamp}
import db.impl.{LoggedActionTable, LoggedActionViewTable}
import db.impl.model.OreModel
import models.user.{LoggedAction, LoggedActionContext}
import ore.user.UserOwned

case class LoggedProject(pId: Option[Int], pPluginId: Option[String], pSlug: Option[String], pOwnerName: Option[String])
case class LoggedProjectVersion(pvId: Option[Int], pvVersionString: Option[String])
case class LoggedProjectPage(ppId: Option[Int], ppSlug: Option[String])
case class LoggedSubject(sId: Option[Int], sName: Option[String])

case class LoggedActionViewModel(override val id: ObjectId = ObjectId.Uninitialized,
                             override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                             private val _userId: Int,
                             private val _address: InetString,
                             private val _action: LoggedAction,
                             private val _actionContext: LoggedActionContext,
                             private val _actionContextId: Int,
                             private val _newState: String,
                             private val _oldState: String,
                             private val _uId: Int,
                             private val _uName: String,
                             private val _loggedProject: LoggedProject,
                             private val _loggedProjectVerison: LoggedProjectVersion,
                             private val _loggedProjectPage: LoggedProjectPage,
                             private val _loggedSubject: LoggedSubject,
                             private val _filterProject: Option[Int],
                             private val _filterVersion: Option[Int],
                             private val _filterPage: Option[Int],
                             private val _filterSubject: Option[Int],
                             private val _filterAction: Option[Int]) extends OreModel(id, createdAt) with UserOwned {

  override type T = LoggedActionViewTable
  override type M = LoggedActionViewModel

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): LoggedActionViewModel = this.copy(createdAt = theTime)
  override def userId: Int = _userId

  def address: InetString = _address
  def action: LoggedAction = _action
  def oldState: String = _oldState
  def newState: String = _newState
  def contextId: Int = _actionContextId
  def actionType: LoggedActionContext = _action.context
  def uId: Int = _uId
  def uName: String = _uName
  def pId: Option[Int] = _loggedProject.pId
  def pPluginId: Option[String] = _loggedProject.pPluginId
  def pSlug: Option[String] = _loggedProject.pSlug
  def pOwnerName: Option[String] = _loggedProject.pOwnerName
  def pvId: Option[Int] = _loggedProjectVerison.pvId
  def pvVersionString: Option[String] = _loggedProjectVerison.pvVersionString
  def ppId: Option[Int] = _loggedProjectPage.ppId
  def ppSlug: Option[String] = _loggedProjectPage.ppSlug
  def sId: Option[Int] = _loggedSubject.sId
  def sName: Option[String] = _loggedSubject.sName
  def filterProject: Option[Int] = _filterProject
  def filterVersion: Option[Int] = _filterVersion
  def filterPage: Option[Int] = _filterPage
  def filterSubject: Option[Int] = _filterSubject
  def filterAction: Option[Int] = _filterAction
}
