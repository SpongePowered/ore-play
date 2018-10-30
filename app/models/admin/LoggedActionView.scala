package models.admin

import db.impl.schema.LoggedActionViewTable
import db.{DbRef, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.{Page, Project, Version}
import models.user.{LoggedAction, LoggedActionContext, User}
import ore.user.UserOwned

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

//TODO: Lot's of existential here, what should they be?

case class LoggedProject(
    pId: Option[DbRef[Project]],
    pPluginId: Option[String],
    pSlug: Option[String],
    pOwnerName: Option[String]
)
case class LoggedProjectVersion(pvId: Option[DbRef[Version]], pvVersionString: Option[String])
case class LoggedProjectPage(ppId: Option[DbRef[Page]], ppSlug: Option[String])
case class LoggedSubject(sId: Option[DbRef[_]], sName: Option[String])

case class LoggedActionViewModel(
    id: ObjId[LoggedActionViewModel] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: DbRef[User],
    address: InetString,
    action: LoggedAction,
    actionContext: LoggedActionContext,
    actionContextId: DbRef[_],
    newState: String,
    oldState: String,
    uId: DbRef[User],
    uName: String,
    loggedProject: LoggedProject,
    loggedProjectVerison: LoggedProjectVersion,
    loggedProjectPage: LoggedProjectPage,
    loggedSubject: LoggedSubject,
    filterProject: Option[DbRef[Project]],
    filterVersion: Option[DbRef[Version]],
    filterPage: Option[DbRef[Page]],
    filterSubject: Option[DbRef[_]],
    filterAction: Option[Int]
) extends Model
    with UserOwned {

  def contextId: DbRef[_]             = actionContextId
  def actionType: LoggedActionContext = action.context
  def pId: Option[DbRef[Project]]     = loggedProject.pId
  def pPluginId: Option[String]       = loggedProject.pPluginId
  def pSlug: Option[String]           = loggedProject.pSlug
  def pOwnerName: Option[String]      = loggedProject.pOwnerName
  def pvId: Option[DbRef[Version]]    = loggedProjectVerison.pvId
  def pvVersionString: Option[String] = loggedProjectVerison.pvVersionString
  def ppId: Option[DbRef[Page]]       = loggedProjectPage.ppId
  def ppSlug: Option[String]          = loggedProjectPage.ppSlug
  def sId: Option[DbRef[_]]           = loggedSubject.sId
  def sName: Option[String]           = loggedSubject.sName

  override type T = LoggedActionViewTable
  override type M = LoggedActionViewModel
}
object LoggedActionViewModel {
  implicit val query: ModelQuery[LoggedActionViewModel] =
    ModelQuery
      .from[LoggedActionViewModel](TableQuery[LoggedActionViewTable], (obj, _, time) => obj.copy(createdAt = time))
}
