package ore.models.statistic

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.VersionDownloadsTable
import ore.db.{DbRef, ModelQuery}
import ore.models.project.Version
import ore.models.user.User

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents a unique download on a Project Version.
  *
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class VersionDownload(
    modelId: DbRef[Version],
    address: InetString,
    cookie: String,
    userId: Option[DbRef[User]]
) extends StatEntry[Version, VersionDownload] {
  override def withUserId(userId: Option[DbRef[User]]): VersionDownload = copy(userId = userId)
}
object VersionDownload
    extends DefaultModelCompanion[VersionDownload, VersionDownloadsTable](TableQuery[VersionDownloadsTable]) {

  implicit val query: ModelQuery[VersionDownload] = ModelQuery.from(this)
}
