package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.VisibilityChangeColumns
import db.table.ModelTable
import models.admin.VersionVisibilityChange
import models.project.Version

class VersionVisibilityChangeTable(tag: RowTag)
    extends ModelTable[VersionVisibilityChange](tag, "project_version_visibility_changes")
    with VisibilityChangeColumns[VersionVisibilityChange] {

  def versionId = column[DbRef[Version]]("version_id")

  override def * =
    mkProj((id.?, createdAt.?, createdBy.?, versionId, comment, resolvedAt.?, resolvedBy.?, visibility))(
      mkTuple[VersionVisibilityChange]()
    )
}
