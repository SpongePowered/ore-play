package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.project.Project
import models.user.User
import ore.db.DbRef

class ProjectStarsTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_stars") {

  def userId    = column[DbRef[User]]("user_id")
  def projectId = column[DbRef[Project]]("project_id")

  override def * = (userId, projectId)
}
