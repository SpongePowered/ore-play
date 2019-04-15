package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import models.project.Project
import models.user.User

class ProjectStarsTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_stars") {

  def userId    = column[DbRef[User]]("user_id")
  def projectId = column[DbRef[Project]]("project_id")

  override def * = (userId, projectId)
}
