package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.Project
import ore.models.user.User
import ore.db.DbRef

class ProjectWatchersTable(tag: Tag) extends AssociativeTable[Project, User](tag, "project_watchers") {

  def projectId = column[DbRef[Project]]("project_id")
  def userId    = column[DbRef[User]]("user_id")

  override def * = (projectId, userId)
}
