package models.admin

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import db.impl.{LoggedActionTable, LoggedActionViewTable}
import db.impl.model.OreModel
import models.user.{LoggedAction, LoggedActionContext}
import ore.user.UserOwned

case class LoggedActionViewModel(override val id: Option[Int] = None,
                             override val createdAt: Option[Timestamp] = None,
                             private val _userId: Int,
                             private val _address: InetString,
                             private val _action: LoggedAction,
                             private val _actionContext: LoggedActionContext,
                             private val _actionContextId: Int,
                             private val _newState: String,
                             private val _oldState: String,
                             private val _uId: Int,
                             private val _uName: String,
                             private val _pId: Option[Int],
                             private val _pPluginId: Option[String],
                             private val _pName: Option[String],
                             private val _pSlug: Option[String],
                             private val _pOwnerName: Option[String],
                             private val _pvId: Option[Int],
                             private val _pvVersionString: Option[String],
                             private val _pvProjectId: Option[Int],
                             private val _pvFileName: Option[String],
                             private val _filterProject: Option[Int],
                             private val _filterVersion: Option[Int]) extends OreModel(id, createdAt) with UserOwned {

  override type T = LoggedActionViewTable
  override type M = LoggedActionViewModel

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): LoggedActionViewModel = this.copy(createdAt = theTime)
  override def userId: Int = _userId

  def address: InetString = _address
  def action: LoggedAction = _action
  def oldState: String = _oldState
  def newState: String = _newState
  def contextId: Int = _actionContextId
  def actionType: LoggedActionContext = _action.context
  def uId: Int = _uId
  def uName: String = _uName
  def pId: Option[Int] = _pId
  def pPluginId: Option[String] = _pPluginId
  def pName: Option[String] = _pName
  def pSlug: Option[String] = _pSlug
  def pOwnerName: Option[String] = _pOwnerName
  def pvId: Option[Int] = _pvId
  def pvVersionString: Option[String] = _pvVersionString
  def pvProjectId: Option[Int] = _pvProjectId
  def pvFileName: Option[String] = _pvFileName
  def filterProject: Option[Int] = _filterProject
}
