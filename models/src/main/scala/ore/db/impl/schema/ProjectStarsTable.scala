package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.Project
import ore.models.user.User

class ProjectStarsTable(tag: Tag) extends AssociativeTable[User, Project](tag, "project_stars") {

  def userId    = column[DbRef[User]]("user_id")
  def projectId = column[DbRef[Project]]("project_id")

  override def * = (userId, projectId)
}
