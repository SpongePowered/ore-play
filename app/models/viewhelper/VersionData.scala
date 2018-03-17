package models.viewhelper

import models.project.{Channel, Project, Version}
import ore.project.Dependency
import ore.project.Dependency._

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

case class VersionData(p: ProjectData, v: Version, c: Channel,
                       approvedBy: Option[String], // Reviewer if present
                       dependencies: Seq[(Dependency, Option[Project])]
                      ) {

  def global = p.global

  def hasUser = p.hasUser
  def currentUser = p.currentUser

  def isRecommended = p.project.recommendedVersionId == v.id

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""


  def filteredDependencies = {
    dependencies.filterNot(_._1.pluginId.equals(SpongeApiId))
      .filterNot(_._1.pluginId.equals(MinecraftId))
      .filterNot(_._1.pluginId.equals(ForgeId))
  }
}
