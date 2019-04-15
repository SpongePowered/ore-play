package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import models.project.{Channel, Project}
import ore.Color
import ore.db.DbRef

class ChannelTable(tag: Tag) extends ModelTable[Channel](tag, "project_channels") with NameColumn[Channel] {

  def color         = column[Color]("color")
  def projectId     = column[DbRef[Project]]("project_id")
  def isNonReviewed = column[Boolean]("is_non_reviewed")

  override def * =
    (id.?, createdAt.?, (projectId, name, color, isNonReviewed)) <> (mkApply((Channel.apply _).tupled), mkUnapply(
      Channel.unapply
    ))
}
