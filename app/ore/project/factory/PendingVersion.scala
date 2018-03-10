package ore.project.factory

import db.impl.access.ProjectBase
import models.project._
import ore.Cacheable
import ore.Colors.Color
import ore.project.Dependency
import ore.project.io.PluginFile
import play.api.cache.CacheApi
import util.PendingAction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Represents a pending version to be created later.
  *
  * @param project        Project version belongs to
  * @param channelName    Name of channel this version will be in
  * @param channelColor   Color of channel for this version
  * @param underlying     Version that is pending
  * @param plugin         Uploaded plugin
  */
// TODO caching?
case class PendingVersion(projects: ProjectBase,
                          factory: ProjectFactory,
                          var project: Project,
                          var channelName: String,
                          var channelColor: Color,
                          underlying: Version,
                          plugin: PluginFile,
                          var createForumPost: Boolean)
                          extends PendingAction[Version] {
  override def complete(implicit ec: ExecutionContext): Future[Version] = {
    this.factory.createVersion(this)
  }

  override def cancel(implicit ec: ExecutionContext) = {
    this.plugin.delete()
    if (this.underlying.isDefined)
      this.projects.deleteVersion(this.underlying)
  }

  // TODO cacheKey override def key: String = this.project.url + '/' + this.underlying.versionString

  def dependenciesAsGhostTags: Seq[Tag] = {
    var ghostFlags: Seq[Tag] = Seq()
    for (dependency <-  this.underlying.dependencies) {
      if (factory.dependencyVersionRegex.pattern.matcher(dependency.version).matches()) {
        if (dependency.pluginId.equalsIgnoreCase(Dependency.SpongeApiId)) {
          ghostFlags = ghostFlags :+ Tag(None, List(), "Sponge", dependency.version, TagColors.Sponge)
        }
        if (dependency.pluginId.equalsIgnoreCase(Dependency.ForgeId)) {
          ghostFlags = ghostFlags :+ Tag(None, List(), "Forge", dependency.version, TagColors.Forge)
        }
      }
    }
    ghostFlags
  }
}
