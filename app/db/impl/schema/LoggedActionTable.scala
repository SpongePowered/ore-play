package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.user.{LoggedAction, LoggedActionContext, LoggedActionModel, User}

import com.github.tminglei.slickpg.InetString

class LoggedActionTable(tag: RowTag) extends ModelTable[LoggedActionModel](tag, "logged_actions") {

  def userId          = column[DbRef[User]]("user_id")
  def address         = column[InetString]("address")
  def action          = column[LoggedAction]("action")
  def actionContext   = column[LoggedActionContext]("action_context")
  def actionContextId = column[DbRef[_]]("action_context_id")
  def newState        = column[String]("new_state")
  def oldState        = column[String]("old_state")

  override def * =
    mkProj((id.?, createdAt.?, userId, address, action, actionContext, actionContextId, newState, oldState))(
      mkTuple[LoggedActionModel]()
    )
}
