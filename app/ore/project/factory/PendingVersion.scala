package ore.project.factory

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.SyncCacheApi

import db.impl.access.ProjectBase
import models.project._
import ore.project.io.PluginFile
import ore.{Cacheable, Color, Platform}

/**
  * Represents a pending version to be created later.
  *
  * @param project        Project version belongs to
  * @param channelName    Name of channel this version will be in
  * @param channelColor   Color of channel for this version
  * @param underlying     Version that is pending
  * @param plugin         Uploaded plugin
  */
case class PendingVersion(
    projects: ProjectBase,
    factory: ProjectFactory,
    var project: Project,
    var channelName: String,
    var channelColor: Color,
    underlying: Version,
    plugin: PluginFile,
    var createForumPost: Boolean,
    override val cacheApi: SyncCacheApi
) extends Cacheable {

  def complete()(implicit ec: ExecutionContext): Future[(Version, Channel, Seq[VersionTag])] = {
    free()
    this.factory.createVersion(this)
  }

  def cancel()(implicit ec: ExecutionContext): Future[Project] = {
    free()
    this.plugin.delete()
    if (this.underlying.isDefined)
      this.projects.deleteVersion(this.underlying)
    else
      Future.successful(project)
  }

  override def key: String = this.project.url + '/' + this.underlying.versionString

  def dependenciesAsGhostTags: Seq[VersionTag] =
    Platform.ghostTags(-1L, this.underlying.dependencies)

}
