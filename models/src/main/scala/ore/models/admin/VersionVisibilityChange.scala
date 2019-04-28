package ore.models.admin

import java.time.Instant

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.VisibilityChange
import ore.db.impl.schema.VersionVisibilityChangeTable
import ore.db.{DbRef, ModelQuery}
import ore.models.project.{Version, Visibility}
import ore.models.user.User

import slick.lifted.TableQuery

case class VersionVisibilityChange(
    createdBy: Option[DbRef[User]],
    versionId: DbRef[Version],
    comment: String,
    resolvedAt: Option[Instant],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
) extends VisibilityChange
object VersionVisibilityChange
    extends DefaultModelCompanion[VersionVisibilityChange, VersionVisibilityChangeTable](
      TableQuery[VersionVisibilityChangeTable]
    ) {

  implicit val query: ModelQuery[VersionVisibilityChange] =
    ModelQuery.from(this)
}
