package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.StatTable
import models.project.Version
import models.statistic.VersionDownload

class VersionDownloadsTable(tag: Tag)
    extends StatTable[Version, VersionDownload](tag, "project_version_downloads", "version_id") {

  override def * = mkProj((id.?, createdAt.?, modelId, address, cookie, userId.?))(mkTuple[VersionDownload]())
}
