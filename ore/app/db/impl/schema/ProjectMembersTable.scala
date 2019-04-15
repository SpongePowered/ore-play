package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.project.Project
import models.user.User
import ore.db.DbRef

class ProjectMembersTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_members") {

  def projectId = column[DbRef[Project]]("project_id")
  def userId    = column[DbRef[User]]("user_id")

  override def * = (userId, projectId)
}
