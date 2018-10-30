package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.admin.{LoggedActionViewModel, LoggedProject, LoggedProjectPage, LoggedProjectVersion, LoggedSubject}
import models.project.{Page, Project, Version}
import models.user.{LoggedAction, LoggedActionContext, User}

import com.github.tminglei.slickpg.InetString

class LoggedActionViewTable(tag: RowTag) extends ModelTable[LoggedActionViewModel](tag, "v_logged_actions") {

  def userId          = column[DbRef[User]]("user_id")
  def address         = column[InetString]("address")
  def action          = column[LoggedAction]("action")
  def actionContext   = column[LoggedActionContext]("action_context")
  def actionContextId = column[DbRef[_]]("action_context_id")
  def newState        = column[String]("new_state")
  def oldState        = column[String]("old_state")
  def uId             = column[DbRef[User]]("u_id")
  def uName           = column[String]("u_name")
  def pId             = column[DbRef[Project]]("p_id")
  def pPluginId       = column[String]("p_plugin_id")
  def pSlug           = column[String]("p_slug")
  def pOwnerName      = column[String]("p_owner_name")
  def pvId            = column[DbRef[Version]]("pv_id")
  def pvVersionString = column[String]("pv_version_string")
  def ppId            = column[DbRef[Page]]("pp_id")
  def ppSlug          = column[String]("pp_slug")
  def sId             = column[DbRef[_]]("s_id")
  def sName           = column[String]("s_name")
  def filterProject   = column[DbRef[Project]]("filter_project")
  def filterVersion   = column[DbRef[Version]]("filter_version")
  def filterPage      = column[DbRef[Page]]("filter_page")
  def filterSubject   = column[DbRef[_]]("filter_subject")
  def filterAction    = column[Int]("filter_action")

  override def * =
    mkProj(
      (
        id.?,
        createdAt.?,
        userId,
        address,
        action,
        actionContext,
        actionContextId,
        newState,
        oldState,
        uId,
        uName,
        loggedProjectProjection,
        loggedProjectVersionProjection,
        loggedProjectPageProjection,
        loggedSubjectProjection,
        filterProject.?,
        filterVersion.?,
        filterPage.?,
        filterSubject.?,
        filterAction.?
      )
    )(mkTuple[LoggedActionViewModel]())

  def loggedProjectProjection =
    (pId.?, pPluginId.?, pSlug.?, pOwnerName.?) <> ((LoggedProject.apply _).tupled, LoggedProject.unapply)
  def loggedProjectVersionProjection =
    (pvId.?, pvVersionString.?) <> ((LoggedProjectVersion.apply _).tupled, LoggedProjectVersion.unapply)
  def loggedProjectPageProjection =
    (ppId.?, ppSlug.?) <> ((LoggedProjectPage.apply _).tupled, LoggedProjectPage.unapply)
  def loggedSubjectProjection = (sId.?, sName.?) <> ((LoggedSubject.apply _).tupled, LoggedSubject.unapply)
}
