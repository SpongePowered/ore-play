package models.admin

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import db.Model
import db.impl.VersionVisibilityChangeTable
import db.impl.model.OreModel
import db.impl.model.common.VisibilityChange
import db.impl.table.ModelKeys._
import models.project.Page
import models.user.User
import play.twirl.api.Html
import util.functional.OptionT
import util.instances.future._

case class VersionVisibilityChange(override val id: Option[Int] = None,
                            override val createdAt: Option[Timestamp] = None,
                            createdBy: Option[Int] = None,
                            projectId: Int = -1,
                            comment: String,
                            var resolvedAt: Option[Timestamp] = None,
                            var resolvedBy: Option[Int] = None,
                            visibility: Int = 1) extends OreModel(id, createdAt) with VisibilityChange {
  /** Self referential type */
  override type M = VersionVisibilityChange
  /** The model's table */
  override type T = VersionVisibilityChangeTable

  /** Render the comment as Html */
  def renderComment(): Html = Page.Render(comment)

  def created(implicit ec: ExecutionContext): OptionT[Future, User] = {
    OptionT.fromOption[Future](createdBy).flatMap(userBase.get(_))
  }

  /**
    * Set the resolvedAt time
    * @param time
    */
  def setResolvedAt(time: Timestamp) = {
    this.resolvedAt = Some(time)
    update(ResolvedAtVC)
  }

  /**
    * Set the resolvedBy user
    * @param user
    */
  def setResolvedBy(user: User) = {
    this.resolvedBy = user.id
    update(ResolvedByVC)
  }
  def setResolvedById(userId: Int) = {
    this.resolvedBy = Some(userId)
    update(ResolvedByVC)
  }

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id      ID to set
    * @param theTime Timestamp
    * @return Copy of model
    */
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): Model = this.copy(id = id, createdAt = createdAt)
}
