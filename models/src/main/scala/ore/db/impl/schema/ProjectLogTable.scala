package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.admin.ProjectLog
import ore.models.project.Project

class ProjectLogTable(tag: Tag) extends ModelTable[ProjectLog](tag, "project_logs") {

  def projectId = column[DbRef[Project]]("project_id")

  override def * = (id.?, createdAt.?, projectId) <> (mkApply(ProjectLog.apply), mkUnapply(ProjectLog.unapply))
}
