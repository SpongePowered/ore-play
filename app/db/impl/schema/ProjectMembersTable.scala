package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import models.project.Project
import models.user.User

class ProjectMembersTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_members") {

  def projectId = column[DbRef[Project]]("project_id")
  def userId    = column[DbRef[User]]("user_id")

  override def * = (userId, projectId)
}
