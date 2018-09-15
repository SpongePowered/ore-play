package models.project

import scala.concurrent.Future

import com.google.common.base.Preconditions._

import db.access.ModelAccess
import db.{Named, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.ChannelTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys
import db.impl.table.ModelKeys._
import ore.Colors._
import ore.permission.scope.ProjectScope

/**
  * Represents a release channel for Project Versions. Each project gets it's
  * own set of channels.
  *
  * @param id           Unique identifier
  * @param createdAt    Instant of creation
  * @param _isNonReviewed Whether this channel should be excluded from the staff
  *                     approval queue
  * @param _name        Name of channel
  * @param _color       Color used to represent this Channel
  * @param projectId    ID of project this channel belongs to
  */
case class Channel(override val id: ObjectId = ObjectId.Uninitialized,
                   override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                   override val projectId: ObjectReference,
                   private var _name: String,
                   private var _color: Color,
                   private var _isNonReviewed: Boolean = false)
                   extends OreModel(id, createdAt)
                     with Named
                     with Ordered[Channel]
                     with ProjectScope {

  override type T = ChannelTable
  override type M = Channel

  def this(name: String, color: Color, projectId: ObjectReference) = this(_name=name, _color=color, projectId=projectId)

  /**
    * Returns the name of this Channel.
    *
    * @return Name of channel
    */
  override def name: String = this._name

  /**
    * Sets the name of this channel.
    *
    * @param _name    New channel name
    */
  def setName(_name: String): Future[Int] = Defined {
    checkNotNull(_name, "null name", "")
    checkArgument(this.config.isValidChannelName(_name), "invalid name", "")
    this._name = _name
    update(Name)
  }

  /**
    * Returns the [[Color]] that this Channel is represented by.
    *
    * @return Color channel is represented by
    */
  def color: Color = this._color

  /**
    * Sets the color of this channel.
    *
    * @param _color Color of channel
    */
  def setColor(_color: Color): Future[Int] = Defined {
    checkNotNull(_color, "null color", "")
    this._color = _color
    update(ModelKeys.Color)
  }

  def isReviewed: Boolean = !this._isNonReviewed

  def isNonReviewed: Boolean = this._isNonReviewed

  def setNonReviewed(isNonReviewed: Boolean): Future[AnyVal] = {
    this._isNonReviewed = isNonReviewed
    if (isDefined)
      update(IsNonReviewed)
    else Future.unit
  }

  /**
    * Returns all Versions in this channel.
    *
    * @return All versions
    */
  def versions: ModelAccess[Version] = this.schema.getChildren[Version](classOf[Version], this)

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Channel = this.copy(id = id, createdAt = theTime)
  override def compare(that: Channel): Int = this._name compare that._name
  override def hashCode(): Int = this.id.value.hashCode
  override def equals(o: Any): Boolean = o.isInstanceOf[Channel] && o.asInstanceOf[Channel].id.value == this.id.value
}

object Channel {

  /**
    * The colors a Channel is allowed to have.
    */
  val Colors: Seq[Color] = Seq(Purple, Violet, Magenta, Blue, Aqua, Cyan, Green,
                               DarkGreen, Chartreuse, Amber, Orange, Red)

}
