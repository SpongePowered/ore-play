package models.project

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Named
import db.impl.schema.ChannelTable
import db.{Model, ModelQuery, ModelService, ObjId, DbRef, ObjectTimestamp}
import ore.Color
import ore.Color._
import ore.project.ProjectOwned

import slick.lifted.TableQuery

/**
  * Represents a release channel for Project Versions. Each project gets it's
  * own set of channels.
  *
  * @param id           Unique identifier
  * @param createdAt    Instant of creation
  * @param isNonReviewed Whether this channel should be excluded from the staff
  *                     approval queue
  * @param name        Name of channel
  * @param color       Color used to represent this Channel
  * @param projectId    ID of project this channel belongs to
  */
case class Channel(
    id: ObjId[Channel] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: DbRef[Project],
    name: String,
    color: Color,
    isNonReviewed: Boolean = false
) extends Model
    with Named
    with ProjectOwned {

  override type T = ChannelTable
  override type M = Channel

  def this(name: String, color: Color, projectId: DbRef[Project]) =
    this(id = ObjId.Uninitialized(), name = name, color = color, projectId = projectId)

  def isReviewed: Boolean = !isNonReviewed

  /**
    * Returns all Versions in this channel.
    *
    * @return All versions
    */
  def versions(implicit service: ModelService): ModelAccess[Version] = service.access(_.channelId === id.value)
}

object Channel {

  implicit val channelsAreOrdered: Ordering[Channel] = (x: Channel, y: Channel) => x.name.compare(y.name)

  implicit val query: ModelQuery[Channel] =
    ModelQuery.from[Channel](TableQuery[ChannelTable], _.copy(_, _))

  /**
    * The colors a Channel is allowed to have.
    */
  val Colors: Seq[Color] =
    Seq(Purple, Violet, Magenta, Blue, Aqua, Cyan, Green, DarkGreen, Chartreuse, Amber, Orange, Red)

}
