package ore.models.admin

import java.time.Instant

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.VisibilityChange
import ore.db.impl.schema.VersionVisibilityChangeTable
import ore.models.project.{Version, Visibility}
import ore.models.user.User
import ore.db.{DbRef, ModelQuery}
import ore.markdown.MarkdownRenderer

import slick.lifted.TableQuery

case class VersionVisibilityChange(
    createdBy: Option[DbRef[User]],
    versionId: DbRef[Version],
    comment: String,
    resolvedAt: Option[Instant],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
) extends VisibilityChange {

  /** Render the comment as Html */
  def renderComment(implicit renderer: MarkdownRenderer): Html = renderer.render(comment)
}
object VersionVisibilityChange
    extends DefaultModelCompanion[VersionVisibilityChange, VersionVisibilityChangeTable](
      TableQuery[VersionVisibilityChangeTable]
    ) {

  implicit val query: ModelQuery[VersionVisibilityChange] =
    ModelQuery.from(this)
}
