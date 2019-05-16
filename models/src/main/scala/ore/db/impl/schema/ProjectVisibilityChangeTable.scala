package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.VisibilityChangeColumns
import ore.models.admin.ProjectVisibilityChange
import ore.models.project.Project

class ProjectVisibilityChangeTable(tag: Tag)
    extends ModelTable[ProjectVisibilityChange](tag, "project_visibility_changes")
    with VisibilityChangeColumns[ProjectVisibilityChange] {

  def projectId = column[DbRef[Project]]("project_id")

  override def * =
    (id.?, createdAt.?, (createdBy.?, projectId, comment, resolvedAt.?, resolvedBy.?, visibility)) <> (mkApply(
      (ProjectVisibilityChange.apply _).tupled
    ), mkUnapply(ProjectVisibilityChange.unapply))
}
