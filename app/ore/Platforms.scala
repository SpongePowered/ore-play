package ore

import models.project.{Tag, TagColors}
import models.project.TagColors.TagColor
import ore.project.Dependency

import scala.language.implicitConversions

/**
  * The Platform a plugin/mod runs on
  *
  * @author phase
  */
object Platforms extends Enumeration {

  val Sponge = Platform(0, "Sponge", SpongeCategory, 0, "spongeapi",
    TagColors.Sponge, "https://spongepowered.org/downloads")

  val SpongeForge = Platform(2, "SpongeForge", SpongeCategory, 2, "spongeforge",
    TagColors.SpongeForge, "https://www.spongepowered.org/downloads/spongeforge")

  val SpongeVanilla = Platform(3, "SpongeVanilla", SpongeCategory, 2, "spongevanilla",
    TagColors.SpongeVanilla, "https://www.spongepowered.org/downloads/spongevanilla")

  val SpongeCommon = Platform(4, "SpongeCommon", SpongeCategory, 1, "sponge",
    TagColors.SpongeCommon, "https://www.spongepowered.org/downloads")

  val Lantern = Platform(5, "Lantern", SpongeCategory, 2, "lantern",
    TagColors.Lantern, "https://www.lanternpowered.org/")

  val Forge = Platform(1, "Forge", ForgeCategory, 0, "forge",
    TagColors.Forge, "https://files.minecraftforge.net/")

  case class Platform(override val id: Int,
                      name: String,
                      platformCategory: PlatformCategory,
                      priority: Int,
                      dependencyId: String,
                      tagColor: TagColor,
                      url: String
                     ) extends super.Val(id, name) {

    def toGhostTag(version: String): Tag = Tag(None, List(), name, version, tagColor)

  }

  implicit def convert(v: Value): Platform = v.asInstanceOf[Platform]

  def getPlatforms(dependencyIds: Seq[String]): Seq[Platform] = {
    Platforms.values
      .filter(p => dependencyIds.contains(p.dependencyId))
      .groupBy(_.platformCategory)
      .flatMap(_._2.groupBy(_.priority).maxBy(_._1)._2)
      .map(_.asInstanceOf[Platform])
      .toSeq
  }

  def getPlatformGhostTags(dependencies: Seq[Dependency]): Seq[Tag] = {
    Platforms.values
      .filter(p => dependencies.map(_.pluginId).contains(p.dependencyId))
      .groupBy(_.platformCategory)
      .flatMap(_._2.groupBy(_.priority).maxBy(_._1)._2)
      .map(p => p.toGhostTag(dependencies.find(_.pluginId == p.dependencyId).get.version))
      .toSeq
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
  val name: String

  def getPlatforms: Seq[Platforms.Value] = {
    Platforms.values.filter(p => p.platformCategory == this).toSeq
  }
}

case object SpongeCategory extends PlatformCategory {
  override val name = "Sponge Plugins"
}

case object ForgeCategory extends PlatformCategory {
  override val name = "Forge Mods"
}

object PlatformCategory {
  def getPlatformCategories: Seq[PlatformCategory] = Seq(SpongeCategory, ForgeCategory)
}
