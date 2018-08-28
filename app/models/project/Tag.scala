package models.project

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import db.impl.{OrePostgresDriver, TagTable}
import db.table.MappedType
import db.{ModelService, Named}
import models.project.TagColors.TagColor
import slick.jdbc.JdbcType

import scala.concurrent.{ExecutionContext, Future}

case class Tag(override val id: Option[Int] = None,
               private var _versionIds: List[Int],
               name: String,
               data: String,
               color: TagColor)
  extends OreModel(id, None)
    with Named {

  override type M = Tag
  override type T = TagTable

  def versionIds: List[Int] = this._versionIds

  def addVersionId(versionId: Int): Unit = {
    this._versionIds = this._versionIds :+ versionId
    if (isDefined) {
      update(TagVersionIds)
    }
  }

  def copyWith(id: Option[Int], theTime: Option[Timestamp]): Tag = this.copy(id = id)

  /**
    * Used to convert a ghost tag to a normal tag
    * @author phase
    */
  def getFilledTag(service: ModelService)(implicit ex: ExecutionContext): Future[Tag] = {
    val access = service.access(classOf[Tag])
    for {
      tagsWithVersion <- access.filter(t => t.name === this.name && t.data === this.data)
      tag <- if (tagsWithVersion.isEmpty) {
        access.add(this)
      } else {
        Future.successful(tagsWithVersion.head)
      }
    } yield tag
  }

}

object TagColors extends Enumeration {

  // Tag colors
  val Sponge = TagColor(1, "#F7Cf0D", "#333333")
  val Forge = TagColor(2, "#dfa86a", "#FFFFFF")
  val Unstable = TagColor(3, "#FFDAB9", "#333333")
  val SpongeForge = TagColor(4, "#910020", "#FFFFFF")
  val SpongeVanilla = TagColor(5, "#50C888", "#FFFFFF")
  val SpongeCommon = TagColor(6, "#5d5dff", "#FFFFFF")
  val Lantern = TagColor(7, "#4EC1B4", "#FFFFFF")
  val Mixin = TagColor(8, "#FFA500", "#333333")

  def withId(id: Int): TagColor = {
    this.apply(id).asInstanceOf[TagColor]
  }

  /** Represents a color. */
  case class TagColor(i: Int, background: String, foreground: String) extends super.Val(i, s"$background $foreground") with MappedType[TagColor] {
    implicit val mapper: JdbcType[TagColor] = OrePostgresDriver.api.tagColorTypeMapper
  }

  implicit def convert(value: Value): TagColor = value.asInstanceOf[TagColor]

}
