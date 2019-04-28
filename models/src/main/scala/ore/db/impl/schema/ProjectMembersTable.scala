package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.Project
import ore.models.user.User

class ProjectMembersTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_members") {

  def projectId = column[DbRef[Project]]("project_id")
  def userId    = column[DbRef[User]]("user_id")

  override def * = (userId, projectId)
}
