package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.StatTable
import models.project.Project
import models.statistic.ProjectView

class ProjectViewsTable(tag: RowTag) extends StatTable[Project, ProjectView](tag, "project_views", "project_id") {

  override def * = mkProj((id.?, createdAt.?, modelId, address, cookie, userId.?))(mkTuple[ProjectView]())
}
