package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.ProjectVisibilityChangeTable
import db.{DbRef, DefaultModelCompanion, ModelQuery}
import models.project.{Project, Visibility}
import models.user.User
import ore.markdown.MarkdownRenderer

import slick.lifted.TableQuery

case class ProjectVisibilityChange(
    createdBy: Option[DbRef[User]],
    projectId: DbRef[Project],
    comment: String,
    resolvedAt: Option[Timestamp],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
) extends VisibilityChange {

  /** Render the comment as Html */
  def renderComment(implicit renderer: MarkdownRenderer): Html = renderer.render(comment)
}
object ProjectVisibilityChange
    extends DefaultModelCompanion[ProjectVisibilityChange, ProjectVisibilityChangeTable](
      TableQuery[ProjectVisibilityChangeTable]
    ) {

  implicit val query: ModelQuery[ProjectVisibilityChange] =
    ModelQuery.from(this)
}
