package models.admin

import java.sql.Timestamp

import play.twirl.api.Html

import db.impl.model.common.VisibilityChange
import db.impl.schema.VersionVisibilityChangeTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjTimestamp}
import models.project.{Page, Version, Visibility}
import models.user.User
import ore.OreConfig

import slick.lifted.TableQuery

case class VersionVisibilityChange private (
    id: ObjId[VersionVisibilityChange],
    createdAt: ObjTimestamp,
    createdBy: Option[DbRef[User]],
    versionId: DbRef[Version],
    comment: String,
    resolvedAt: Option[Timestamp],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
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
  def partial(
      createdBy: Option[DbRef[User]] = None,
      versionId: DbRef[Version],
      comment: String,
      resolvedAt: Option[Timestamp] = None,
      resolvedBy: Option[DbRef[User]] = None,
      visibility: Visibility = Visibility.New
  ): InsertFunc[VersionVisibilityChange] =
    (id, time) => VersionVisibilityChange(id, time, createdBy, versionId, comment, resolvedAt, resolvedBy, visibility)

  implicit val query: ModelQuery[VersionVisibilityChange] =
    ModelQuery.from[VersionVisibilityChange](TableQuery[VersionVisibilityChangeTable], _.copy(_, _))
}
