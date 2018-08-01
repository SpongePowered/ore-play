package models.admin

import java.sql.Timestamp

import db.ObjectReference

import db.{Model, ObjectId, ObjectTimestamp}
import models.project.Page
import play.twirl.api.Html

import db.impl.ProjectVisibilityChangeTable
import db.impl.model.common.VisibilityChange
import ore.OreConfig

case class ProjectVisibilityChange(id: ObjectId = ObjectId.Uninitialized,
                            createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                            createdBy: Option[ObjectReference] = None,
                            projectId: ObjectReference = -1,
                            comment: String,
                            resolvedAt: Option[Timestamp] = None,
                            resolvedBy: Option[ObjectReference] = None,
                            visibility: Int = 1) extends Model with VisibilityChange {
  /** Self referential type */
  override type M = ProjectVisibilityChange
  /** The model's table */
  override type T = ProjectVisibilityChangeTable

  /** Render the comment as Html */
  def renderComment(implicit config: OreConfig): Html = Page.render(comment)

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id      ID to set
    * @param theTime Timestamp
    * @return Copy of model
    */
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = createdAt)
}
