package ore.data

import scala.collection.immutable

import ore.models.project.TagColor

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
    val tagColor: TagColor,
    val url: String,
    val noVersionPolicy: Platform.NoVersionPolicy = Platform.NoVersionPolicy.NotAllowed
) extends StringEnumEntry {

  def name: String = value
}
object Platform extends StringEnum[Platform] {

  val values: immutable.IndexedSeq[Platform] = findValues

  case object Sponge
      extends Platform(
        "spongeapi",
        SpongeCategory,
        0,
        "spongeapi",
        TagColor.Sponge,
        "https://spongepowered.org/downloads"
      )

  case object SpongeForge
      extends Platform(
        "spongeforge",
        SpongeCategory,
        2,
        "spongeforge",
        TagColor.SpongeForge,
        "https://www.spongepowered.org/downloads/spongeforge"
      )

  case object SpongeVanilla
      extends Platform(
        "spongevanilla",
        SpongeCategory,
        2,
        "spongevanilla",
        TagColor.SpongeVanilla,
        "https://www.spongepowered.org/downloads/spongevanilla"
      )

  case object SpongeCommon
      extends Platform(
        "sponge",
        SpongeCategory,
        1,
        "sponge",
        TagColor.SpongeCommon,
        "https://www.spongepowered.org/downloads"
      )

  case object Lantern
      extends Platform("lantern", SpongeCategory, 2, "lantern", TagColor.Lantern, "https://www.lanternpowered.org/")

  case object Forge
      extends Platform("forge", ForgeCategory, 0, "forge", TagColor.Forge, "https://files.minecraftforge.net/")

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
