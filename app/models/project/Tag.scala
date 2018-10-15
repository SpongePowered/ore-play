package models.project

import java.sql.Timestamp
import java.time.Instant

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.impl.schema.TagTable
import db.{Model, ModelQuery, ModelService, Named, ObjectId, ObjectReference, ObjectTimestamp}

import enumeratum.values._
import slick.lifted.TableQuery

case class Tag(
    id: ObjectId = ObjectId.Uninitialized,
    versionIds: List[ObjectReference],
    name: String,
    data: String,
    color: TagColor
) extends Model
    with Named {

  override val createdAt: ObjectTimestamp = ObjectTimestamp(Timestamp.from(Instant.EPOCH))

  override type M = Tag
  override type T = TagTable

  /**
    * Used to convert a ghost tag to a normal tag
    * @author phase
    */
  def getFilledTag(service: ModelService)(implicit ex: ExecutionContext): Future[Tag] = {
    val access = service.access[Tag]()
    for {
      tagsWithVersion <- access.filter(t => t.name === this.name && t.data === this.data)
      tag <- if (tagsWithVersion.isEmpty) {
        access.add(this)
      } else {
        Future.successful(tagsWithVersion.head)
      }
    } yield tag
  }

  def copyWith(id: ObjectId, theTime: ObjectTimestamp): Tag = this.copy(id = id)
}
object Tag {
  implicit val query: ModelQuery[Tag] =
    ModelQuery.from[Tag](TableQuery[TagTable])
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
