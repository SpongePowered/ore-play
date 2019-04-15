package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityChangeColumns
import models.admin.ProjectVisibilityChange
import models.project.Project
import ore.db.DbRef

class ProjectVisibilityChangeTable(tag: Tag)
    extends ModelTable[ProjectVisibilityChange](tag, "project_visibility_changes")
    with VisibilityChangeColumns[ProjectVisibilityChange] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * =
    (id.?, createdAt.?, (createdBy.?, projectId, comment, resolvedAt.?, resolvedBy.?, visibility)) <> (mkApply(
      (ProjectVisibilityChange.apply _).tupled
    ), mkUnapply(ProjectVisibilityChange.unapply))
}
