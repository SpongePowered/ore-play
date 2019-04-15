package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityChangeColumns
import models.admin.VersionVisibilityChange
import models.project.Version
import ore.db.DbRef

class VersionVisibilityChangeTable(tag: Tag)
    extends ModelTable[VersionVisibilityChange](tag, "project_version_visibility_changes")
    with VisibilityChangeColumns[VersionVisibilityChange] {

  def versionId = column[DbRef[Version]]("version_id")

  override def * =
    (id.?, createdAt.?, (createdBy.?, versionId, comment, resolvedAt.?, resolvedBy.?, visibility)) <> (mkApply(
      (VersionVisibilityChange.apply _).tupled
    ), mkUnapply(VersionVisibilityChange.unapply))
}
