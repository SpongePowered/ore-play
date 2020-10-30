package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.StatTable
import ore.models.project.Project
import ore.models.statistic.ProjectView

class ProjectViewsTable(tag: Tag) extends StatTable[Project, ProjectView](tag, "project_views", "project_id") {

  override def * =
    (id.?, createdAt.?, (modelId, address, cookie, userId.?)).<>(
      mkApply((ProjectView.apply _).tupled),
      mkUnapply(
        ProjectView.unapply
      )
    )
}
