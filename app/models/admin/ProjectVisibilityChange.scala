package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.ProjectVisibilityChangeTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.{Page, Project, Visibility}
import models.user.User
import ore.OreConfig

import slick.lifted.TableQuery

case class ProjectVisibilityChange(
    id: ObjId[ProjectVisibilityChange],
    createdAt: ObjectTimestamp,
    createdBy: Option[DbRef[User]],
    projectId: DbRef[Project],
    comment: String,
    resolvedAt: Option[Timestamp],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
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
  def partial(
      createdBy: Option[DbRef[User]] = None,
      projectId: DbRef[Project],
      comment: String,
      resolvedAt: Option[Timestamp] = None,
      resolvedBy: Option[DbRef[User]] = None,
      visibility: Visibility = Visibility.New
  ): InsertFunc[ProjectVisibilityChange] =
    (id, time) => ProjectVisibilityChange(id, time, createdBy, projectId, comment, resolvedAt, resolvedBy, visibility)

  implicit val query: ModelQuery[ProjectVisibilityChange] =
    ModelQuery.from[ProjectVisibilityChange](TableQuery[ProjectVisibilityChangeTable], _.copy(_, _))
}
