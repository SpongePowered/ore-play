package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.VersionVisibilityChangeTable
import db.{DbRef, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.{Page, Version, Visibility}
import models.user.User
import ore.OreConfig

import slick.lifted.TableQuery

case class VersionVisibilityChange(
    id: ObjId[VersionVisibilityChange] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    createdBy: Option[DbRef[User]] = None,
    versionId: DbRef[Version],
    comment: String,
    resolvedAt: Option[Timestamp] = None,
    resolvedBy: Option[DbRef[User]] = None,
    visibility: Visibility = Visibility.New
) extends Model
    with VisibilityChange {

  /** Self referential type */
  override type M = VersionVisibilityChange

  /** The model's table */
  override type T = VersionVisibilityChangeTable

  /** Render the comment as Html */
  def renderComment(implicit config: OreConfig): Html = Page.render(comment)
}
object VersionVisibilityChange {
  implicit val query: ModelQuery[VersionVisibilityChange] =
    ModelQuery.from[VersionVisibilityChange](TableQuery[VersionVisibilityChangeTable], _.copy(_, _))
}
