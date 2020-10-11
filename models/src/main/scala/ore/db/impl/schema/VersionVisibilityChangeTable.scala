package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.VisibilityChangeColumns
import ore.models.admin.VersionVisibilityChange
import ore.models.project.Version

class VersionVisibilityChangeTable(tag: Tag)
    extends ModelTable[VersionVisibilityChange](tag, "project_version_visibility_changes")
    with VisibilityChangeColumns[VersionVisibilityChange] {

  def versionId = column[DbRef[Version]]("version_id")

  override def * =
    (id.?, createdAt.?, (createdBy.?, versionId, comment, resolvedAt.?, resolvedBy.?, visibility)).<>(
      mkApply(
        (VersionVisibilityChange.apply _).tupled
      ),
      mkUnapply(VersionVisibilityChange.unapply)
    )
}
