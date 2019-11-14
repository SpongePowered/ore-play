package ore.models.project

import java.time.OffsetDateTime

import scala.collection.immutable

import ore.data.Platform
import ore.db._
import ore.db.impl.ModelCompanionPartial
import ore.db.impl.common.Named
import ore.db.impl.schema.VersionTagTable

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import cats.syntax.all._
import enumeratum.values._
import slick.lifted.TableQuery

case class VersionTag(
    versionId: DbRef[Version],
    name: String,
    data: Option[String],
    color: TagColor,
    platformVersion: Option[String]
) extends Named
object VersionTag extends ModelCompanionPartial[VersionTag, VersionTagTable](TableQuery[VersionTagTable]) {

  override def asDbModel(
      model: VersionTag,
      id: ObjId[VersionTag],
      time: ObjOffsetDateTime
  ): Model[VersionTag] = Model(id, ObjOffsetDateTime(OffsetDateTime.MIN), model)

  implicit val query: ModelQuery[VersionTag] = ModelQuery.from(this)

  def userTagsToReal(
      name: String,
      data: Seq[String],
      versionId: DbRef[VersionTag]
  ): ValidatedNel[String, (List[String], List[VersionTag])] = {
    val platformsWithName = Platform.valuesToEntriesMap.map[String, Platform](t => t._2.name -> t._2)

    //If the tag is a platform we want to treat it differently
    platformsWithName.get(name) match {
      case Some(platform) =>
        if (data.nonEmpty) {
          platform.createTag(versionId, None).map(t => t._1.toList -> List(t._2))
        } else {
          data
            .map(v => platform.createTag(versionId, Some(v)))
            .toList
            .sequence
            .map(v => v.flatMap(_._1) -> v.map(_._2))
        }
      case None =>
        TagType.withValueOpt(name) match {
          case Some(tagType) => tagType.createTagUnsanitized(data, versionId)
          case None          => Validated.invalidNel(s"$name is not a valid tag")
        }
    }
  }

  sealed trait TagType extends StringEnumEntry {
    type Data
    def name: String

    def value: String = name

    def tagColor(data: Data): TagColor

    def stringyfyData(data: Data): Option[String]

    def createTag(data: Data, versionId: DbRef[Version]): VersionTag =
      VersionTag(versionId, name, stringyfyData(data), tagColor(data), None)

    def parseData(data: Seq[String]): ValidatedNel[String, (List[String], List[Data])]

    def createTagUnsanitized(
        strData: Seq[String],
        versionId: DbRef[Version]
    ): Validated[NonEmptyList[String], (List[String], List[VersionTag])] =
      parseData(strData).map(t => t._1 -> t._2.map(createTag(_, versionId)))
  }
  object TagType extends StringEnum[TagType] {
    override def values: IndexedSeq[TagType] = findValues
  }

  object MixinTag extends TagType {
    override type Data = Unit

    override def name: String = "mixin"

    override def tagColor(values: Unit): TagColor = TagColor.Mixin

    override def stringyfyData(values: Unit): Option[String] = None

    override def parseData(data: Seq[String]): ValidatedNel[String, (List[String], List[Unit])] =
      Validated.validNel((if (data.nonEmpty) List("tags.mixin.warnings.noData") else Nil, List(())))
  }

  object StabilityTag extends TagType {
    override type Data = StabilityValues

    override def name: String = "stability"

    override def tagColor(values: StabilityValues): TagColor = values.color

    override def stringyfyData(values: StabilityValues): Option[String] = Some(values.value)

    override def parseData(data: Seq[String]): ValidatedNel[String, (List[String], List[StabilityValues])] =
      data.headOption
        .toValidNel("tags.stability.errors.noData")
        .andThen { s =>
          StabilityValues
            .withValueOpt(s)
            .map(s => (if (data.lengthIs > 1) List("tags.stability.warnings.onlyOne") else Nil, List(s)))
            .toValidNel("tags.stability.errors.invalidStability")
        }

    sealed abstract class StabilityValues(val value: String, val color: TagColor) extends StringEnumEntry
    object StabilityValues extends StringEnum[StabilityValues] {
      override def values: IndexedSeq[StabilityValues] = findValues

      case object Stable      extends StabilityValues("stable", TagColor.Stable)
      case object Beta        extends StabilityValues("beta", TagColor.Beta)
      case object Alpha       extends StabilityValues("alpha", TagColor.Alpha)
      case object Bleeding    extends StabilityValues("bleeding", TagColor.Bleeding)
      case object Unsupported extends StabilityValues("unsupported", TagColor.Unsupported)
      case object Broken      extends StabilityValues("broken", TagColor.Broken)
    }
  }

  object ReleaseTypeTag extends TagType {
    override type Data = ReleaseTypeValues

    override def name: String = "release_type"

    override def tagColor(values: ReleaseTypeValues): TagColor = values.color

    override def stringyfyData(values: ReleaseTypeValues): Option[String] = Some(values.value)

    override def parseData(data: Seq[String]): ValidatedNel[String, (List[String], List[ReleaseTypeValues])] =
      data.headOption
        .toValidNel("tags.release_type.errors.noData")
        .andThen { s =>
          ReleaseTypeValues
            .withValueOpt(s)
            .map(s => (if (data.lengthIs > 1) List("tags.release_type.warnings.onlyOne") else Nil, List(s)))
            .toValidNel("tags.release_type.errors.invalidReleaseType")
        }

    sealed abstract class ReleaseTypeValues(val value: String, val color: TagColor) extends StringEnumEntry
    object ReleaseTypeValues extends StringEnum[ReleaseTypeValues] {
      override def values: IndexedSeq[ReleaseTypeValues] = findValues

      case object MajorUpdate extends ReleaseTypeValues("major_update", TagColor.MajorUpdate)
      case object MinorUpdate extends ReleaseTypeValues("minor_update", TagColor.MinorUpdate)
      case object Patches     extends ReleaseTypeValues("patches", TagColor.Patches)
      case object Hotfix      extends ReleaseTypeValues("hotfix", TagColor.Hotfix)
    }
  }
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

  //From the normal color enum
  case object Purple      extends TagColor(9, "#B400FF", "#FFFFFF")
  case object Violet      extends TagColor(10, "#C87DFF", "#FFFFFF")
  case object Magenta     extends TagColor(11, "#E100E1", "#FFFFFF")
  case object Blue        extends TagColor(12, "#0000FF", "#FFFFFF")
  case object LightBlue   extends TagColor(13, "#B9F2FF", "#FFFFFF")
  case object Quartz      extends TagColor(14, "#E7FEFF", "#FFFFFF")
  case object Aqua        extends TagColor(15, "#0096FF", "#FFFFFF")
  case object Cyan        extends TagColor(16, "#00E1E1", "#FFFFFF")
  case object Green       extends TagColor(17, "#00DC00", "#FFFFFF")
  case object DarkGreen   extends TagColor(18, "#009600", "#FFFFFF")
  case object Chartreuse  extends TagColor(19, "#7FFF00", "#FFFFFF")
  case object Amber       extends TagColor(20, "#FFC800", "#FFFFFF")
  case object Gold        extends TagColor(21, "#CFB53B", "#FFFFFF")
  case object Orange      extends TagColor(22, "#FF8200", "#FFFFFF")
  case object Red         extends TagColor(23, "#DC0000", "#FFFFFF")
  case object Silver      extends TagColor(24, "#C0C0C0", "#FFFFFF")
  case object Gray        extends TagColor(25, "#A9A9A9", "#FFFFFF")
  case object Transparent extends TagColor(26, "transparent", "#FFFFFF")

  case object Stable      extends TagColor(27, "00DC00", "#333333")
  case object Beta        extends TagColor(28, "FFC800", "#333333")
  case object Alpha       extends TagColor(29, "FF8200", "#333333")
  case object Bleeding    extends TagColor(30, "#DC0000", "#333333")
  case object Unsupported extends TagColor(31, "#7F7F7F", "#FFFFFF")
  case object Broken      extends TagColor(32, "#565656", "#FFFFFF")

  case object MajorUpdate extends TagColor(33, "#CFB53B", "#333333")
  case object MinorUpdate extends TagColor(34, "#C0C0C0", "#333333")
  case object Patches     extends TagColor(35, "#7F7F7F", "#FFFFFF")
  case object Hotfix      extends TagColor(36, "#DC0000", "#333333")
}
