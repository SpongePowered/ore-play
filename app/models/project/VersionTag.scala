package models.project

import java.sql.Timestamp
import java.time.Instant

import scala.collection.immutable

import db.impl.model.common.Named
import db.impl.schema.VersionTagTable
import models.querymodels.ViewTag
import db.{Model, ModelQuery, ObjId, DbRef, ObjectTimestamp}

import enumeratum.values._
import slick.lifted.TableQuery

case class VersionTag(
    id: ObjId[VersionTag] = ObjId.Uninitialized(),
    versionId: DbRef[Version],
    name: String,
    data: String,
    color: TagColor
) extends Model
    with Named {

  override val createdAt: ObjectTimestamp = ObjectTimestamp(Timestamp.from(Instant.EPOCH))

  override type M = VersionTag
  override type T = VersionTagTable

  def asViewTag: ViewTag = ViewTag(name, data, color)
}
object VersionTag {
  implicit val query: ModelQuery[VersionTag] =
    ModelQuery.from[VersionTag](TableQuery[VersionTagTable], (obj, id, _) => obj.copy(id))
}

sealed abstract class TagColor(val value: Int, val background: String, val foreground: String) extends IntEnumEntry
object TagColor extends IntEnum[TagColor] {

  val values: immutable.IndexedSeq[TagColor] = findValues

  // Tag colors
  case object Sponge        extends TagColor(1, "#F7Cf0D", "#333333")
  case object Forge         extends TagColor(2, "#dfa86a", "#FFFFFF")
  case object Unstable      extends TagColor(3, "#FFDAB9", "#333333")
  case object SpongeForge   extends TagColor(4, "#910020", "#FFFFFF")
  case object SpongeVanilla extends TagColor(5, "#50C888", "#FFFFFF")
  case object SpongeCommon  extends TagColor(6, "#5d5dff", "#FFFFFF")
  case object Lantern       extends TagColor(7, "#4EC1B4", "#FFFFFF")
  case object Mixin         extends TagColor(8, "#FFA500", "#333333")
}
