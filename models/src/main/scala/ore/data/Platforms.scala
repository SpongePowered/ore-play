package ore.data

import scala.collection.immutable
import scala.util.matching.Regex

import ore.data.Platform.NoVersionPolicy

import cats.data.Writer
import cats.instances.option._
import cats.syntax.all._
import enumeratum.values._

/**
  * The Platform a plugin/mod runs on
  *
  * @author phase
  */
sealed abstract class Platform(
    val value: String,
    val platformCategory: PlatformCategory,
    val priority: Int,
    val dependencyId: String,
    val url: String,
    val coarseVersionRegex: Option[Regex],
    val noVersionPolicy: Platform.NoVersionPolicy = Platform.NoVersionPolicy.NotAllowed
) extends StringEnumEntry {

  def name: String = value

  def coarseVersionOf(version: String): String = coarseVersionRegex.fold(version) { extractor =>
    version match {
      case extractor(coarseStrVersion) => coarseStrVersion
      case _                           => version
    }
  }

  def produceVersionWarning(version: Option[String]): Writer[List[String], Unit] = {
    val inverseVersion = version.fold(Some(""): Option[String])(_ => None)
    noVersionPolicy match {
      case NoVersionPolicy.NotAllowed =>
        Writer.tell(
          inverseVersion.as(s"A missing version for the platform $name will not be accepted in the future").toList
        )
      case NoVersionPolicy.Warning =>
        Writer.tell(inverseVersion.as(s"You are recommended to supply a version for the platform $name").toList)
      case NoVersionPolicy.Allowed => Writer.tell(Nil)
    }
  }
}
object Platform extends StringEnum[Platform] {

  val values: immutable.IndexedSeq[Platform] = findValues

  case object Sponge
      extends Platform(
        "spongeapi",
        SpongeCategory,
        0,
        "spongeapi",
        "https://spongepowered.org/downloads",
        Some("""^(\d+)\.\d+(?:\.\d+)?(?:-SNAPSHOT)?(?:-[a-z0-9]{7,9})?""".r)
      )

  case object SpongeForge
      extends Platform(
        "spongeforge",
        SpongeCategory,
        2,
        "spongeforge",
        "https://www.spongepowered.org/downloads/spongeforge",
        Some("""^\d+\.\d+\.\d+-\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$""".r)
      )

  case object SpongeVanilla
      extends Platform(
        "spongevanilla",
        SpongeCategory,
        2,
        "spongevanilla",
        "https://www.spongepowered.org/downloads/spongevanilla",
        Some("""^\d+\.\d+(?:\.\d+)?-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$""".r)
      )

  case object SpongeCommon
      extends Platform(
        "sponge",
        SpongeCategory,
        1,
        "sponge",
        "https://www.spongepowered.org/downloads",
        None
      )

  case object Lantern
      extends Platform(
        "lantern",
        SpongeCategory,
        2,
        "lantern",
        "https://www.lanternpowered.org/",
        None
      )

  case object Forge
      extends Platform(
        "forge",
        ForgeCategory,
        0,
        "forge",
        "https://files.minecraftforge.net/",
        Some("""^(?:\d+\.)?(\d+)\.\d+\.\d+$""".r)
      )

  def createVersionedPlatforms(
      dependencyIds: Seq[String],
      dependencyVersions: Seq[Option[String]]
  ): Writer[List[String], List[VersionedPlatform]] = {
    import cats.instances.list._
    dependencyIds
      .zip(dependencyVersions)
      .flatMap {
        case (depId, depVersion) =>
          withValueOpt(depId).map { platform =>
            platform
              .produceVersionWarning(depVersion)
              .as(VersionedPlatform(platform.name, depVersion, depVersion.map(platform.coarseVersionOf)))
          }
      }
      .toList
      .sequence
  }

  def getPlatforms(dependencyIds: Seq[String]): Seq[Platform] = {
    Platform.values
      .filter(p => dependencyIds.contains(p.dependencyId))
      .groupBy(_.platformCategory)
      .flatMap(_._2.groupBy(_.priority).maxBy(_._1)._2)
      .toSeq
  }

  sealed trait NoVersionPolicy
  object NoVersionPolicy {
    case object NotAllowed extends NoVersionPolicy
    case object Warning    extends NoVersionPolicy
    case object Allowed    extends NoVersionPolicy
  }
}

/**
  * The category of a platform.
  * Examples would be
  *
  * Sponge <- SpongeAPI, SpongeForge, SpongeVanilla
  * Forge <- Forge (maybe Rift if that doesn't die?)
  * Bukkit <- Bukkit, Spigot, Paper
  * Canary <- Canary, Neptune
  *
  * @author phase
  */
sealed trait PlatformCategory {
  def name: String
  def tagName: String

  def getPlatforms: Seq[Platform] = Platform.values.filter(_.platformCategory == this)
}

case object SpongeCategory extends PlatformCategory {
  val name    = "Sponge Plugins"
  val tagName = "Sponge"
}

case object ForgeCategory extends PlatformCategory {
  val name    = "Forge Mods"
  val tagName = "Forge"
}

object PlatformCategory {
  def getPlatformCategories: Seq[PlatformCategory] = Seq(SpongeCategory, ForgeCategory)
}

case class VersionedPlatform(id: String, version: Option[String], coarseVersion: Option[String])
