package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import models.project.Project
import models.user.User

class ProjectWatchersTable(tag: Tag) extends AssociativeTable[Project, User](tag, "project_watchers") {

  def projectId = column[DbRef[Project]]("project_id")
  def userId    = column[DbRef[User]]("user_id")

  override def * = (projectId, userId)
}
