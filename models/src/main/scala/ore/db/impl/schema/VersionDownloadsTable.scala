package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.StatTable
import ore.models.project.Version
import ore.models.statistic.VersionDownload

class VersionDownloadsTable(tag: Tag)
    extends StatTable[Version, VersionDownload](tag, "project_version_downloads", "version_id") {

  override def * =
    (id.?, createdAt.?, (modelId, address, cookie, userId.?)).<>(
      mkApply((VersionDownload.apply _).tupled),
      mkUnapply(
        VersionDownload.unapply
      )
    )
}
