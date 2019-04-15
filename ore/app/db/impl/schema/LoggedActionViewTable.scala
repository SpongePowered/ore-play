package db.impl.schema

import java.sql.Timestamp

import db.{Model, DbRef, ObjId, ObjTimestamp}
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.admin.{LoggedActionViewModel, LoggedProject, LoggedProjectPage, LoggedProjectVersion, LoggedSubject}
import models.project.{Page, Project, Version}
import models.user.{LoggedAction, LoggedActionContext, User}

import com.github.tminglei.slickpg.InetString

class LoggedActionViewTable[Ctx](tag: Tag) extends ModelTable[LoggedActionViewModel[Ctx]](tag, "v_logged_actions") {

  def userId          = column[DbRef[User]]("user_id")
  def address         = column[InetString]("address")
  def action          = column[LoggedAction[Ctx]]("action")
  def actionContext   = column[LoggedActionContext[Ctx]]("action_context")
  def actionContextId = column[DbRef[Ctx]]("action_context_id")
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
  def ppName          = column[String]("pp_name")
  def ppSlug          = column[String]("pp_slug")
  def sId             = column[DbRef[_]]("s_id")
  def sName           = column[String]("s_name")
  def filterProject   = column[DbRef[Project]]("filter_project")
  def filterVersion   = column[DbRef[Version]]("filter_version")
  def filterPage      = column[DbRef[Page]]("filter_page")
  def filterSubject   = column[DbRef[_]]("filter_subject")
  def filterAction    = column[Int]("filter_action")

  private def rawApply(
      id: Option[DbRef[LoggedActionViewModel[Ctx]]],
      createdAt: Option[Timestamp],
      userId: DbRef[User],
      address: InetString,
      action: LoggedAction[Ctx],
      actionContext: LoggedActionContext[Ctx],
      actionContextId: DbRef[Ctx],
      newState: String,
      oldState: String,
      uId: DbRef[User],
      uName: String,
      loggedProject: LoggedProject,
      loggedProjectVersion: LoggedProjectVersion,
      loggedProjectPage: LoggedProjectPage,
      loggedSubject: LoggedSubject,
      filterProject: Option[DbRef[Project]],
      filterVersion: Option[DbRef[Version]],
      filterPage: Option[DbRef[Page]],
      filterSubject: Option[DbRef[_]],
      filterAction: Option[Int]
  ) = Model(
    ObjId.unsafeFromOption(id),
    ObjTimestamp.unsafeFromOption(createdAt),
    LoggedActionViewModel[Ctx](
      userId,
      address,
      action,
      actionContext,
      actionContextId,
      newState,
      oldState,
      uId,
      uName,
      loggedProject,
      loggedProjectVersion,
      loggedProjectPage,
      loggedSubject,
      filterProject,
      filterVersion,
      filterPage,
      filterSubject,
      filterAction
    )
  )

  private def rawUnapply(m: Model[LoggedActionViewModel[Ctx]]) = m match {
    case Model(
        id,
        createdAt,
        LoggedActionViewModel(
          userId,
          address,
          action,
          actionContext,
          actionContextId,
          newState,
          oldState,
          uId,
          uName,
          loggedProject,
          loggedProjectVersion,
          loggedProjectPage,
          loggedSubject,
          filterProject,
          filterVersion,
          filterPage,
          filterSubject,
          filterAction
        )
        ) =>
      Some(
        (
          id.unsafeToOption,
          createdAt.unsafeToOption,
          userId,
          address,
          action,
          actionContext,
          actionContextId,
          newState,
          oldState,
          uId,
          uName,
          loggedProject,
          loggedProjectVersion,
          loggedProjectPage,
          loggedSubject,
          filterProject,
          filterVersion,
          filterPage,
          filterSubject,
          filterAction
        )
      )
    case _ => None
  }

  override def * = {
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
    ) <> ((rawApply _).tupled, rawUnapply)
  }

  def loggedProjectProjection =
    (pId.?, pPluginId.?, pSlug.?, pOwnerName.?) <> ((LoggedProject.apply _).tupled, LoggedProject.unapply)
  def loggedProjectVersionProjection =
    (pvId.?, pvVersionString.?) <> ((LoggedProjectVersion.apply _).tupled, LoggedProjectVersion.unapply)
  def loggedProjectPageProjection =
    (ppId.?, ppName.?, ppSlug.?) <> ((LoggedProjectPage.apply _).tupled, LoggedProjectPage.unapply)
  def loggedSubjectProjection = (sId.?, sName.?) <> ((LoggedSubject.apply _).tupled, LoggedSubject.unapply)
}
