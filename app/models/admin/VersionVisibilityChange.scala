package models.admin

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.VersionVisibilityChangeTable
import db.impl.model.OreModel
import db.impl.model.common.VisibilityChange
import db.impl.table.ModelKeys._
import models.project.Page
import models.user.User
import play.twirl.api.Html
import util.functional.OptionT
import util.instances.future._

case class VersionVisibilityChange(override val id: ObjectId = ObjectId.Uninitialized,
                            override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                            createdBy: Option[ObjectReference] = None,
                            projectId: ObjectReference = -1,
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
  def setResolvedAt(time: Timestamp): Future[Int] = {
    this.resolvedAt = Some(time)
    update(ResolvedAtVC)
  }

  /**
    * Set the resolvedBy user
    * @param user
    */
  def setResolvedBy(user: User): Future[Int] = setResolvedById(user.id.value)
  def setResolvedById(userId: Int): Future[Int] = {
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
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = createdAt)
}
