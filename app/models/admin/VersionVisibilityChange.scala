package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.VersionVisibilityChangeTable
import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import models.project.{Page, Visibility}
import ore.OreConfig

case class VersionVisibilityChange(
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
  override type M = VersionVisibilityChange

  /** The model's table */
  override type T = VersionVisibilityChangeTable

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
