package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.ProjectVisibilityChangeTable
import db.{Model, ModelQuery, ObjectId, ObjectReference, ObjectTimestamp}
import models.project.{Page, Visibility}
import ore.OreConfig

import slick.lifted.TableQuery

case class ProjectVisibilityChange(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    createdBy: Option[ObjectReference] = None,
    projectId: ObjectReference,
    comment: String,
    resolvedAt: Option[Timestamp] = None,
    resolvedBy: Option[ObjectReference] = None,
    visibility: Visibility = Visibility.New
) extends Model
    with VisibilityChange {

  /** Self referential type */
  override type M = ProjectVisibilityChange

  /** The model's table */
  override type T = ProjectVisibilityChangeTable

  /** Render the comment as Html */
  def renderComment(implicit config: OreConfig): Html = Page.render(comment)
}
object ProjectVisibilityChange {
  implicit val query: ModelQuery[ProjectVisibilityChange] =
    ModelQuery.from[ProjectVisibilityChange](TableQuery[ProjectVisibilityChangeTable], _.copy(_, _))
}
