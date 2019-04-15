package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.admin.ProjectLog
import models.project.Project
import ore.db.DbRef

class ProjectLogTable(tag: Tag) extends ModelTable[ProjectLog](tag, "project_logs") {

  def projectId = column[DbRef[Project]]("project_id")

  override def * = (id.?, createdAt.?, projectId) <> (mkApply(ProjectLog.apply), mkUnapply(ProjectLog.unapply))
}
