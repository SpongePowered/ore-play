package ore.project.factory

import scala.concurrent.ExecutionContext

import play.api.cache.SyncCacheApi

import db.impl.access.ProjectBase
import models.project._
import ore.project.io.PluginFileWithData
import ore.{Cacheable, Color, Platform}

import cats.effect.{ContextShift, IO}
import cats.syntax.all._

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
    plugin: PluginFileWithData,
    var createForumPost: Boolean,
    override val cacheApi: SyncCacheApi
) extends Cacheable {

  def complete()(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[(Version, Channel, Seq[VersionTag])] =
    free *> this.factory.createVersion(this)

  def cancel()(implicit cs: ContextShift[IO]): IO[Project] =
    free *> this.plugin.delete *> (if (this.underlying.isDefined) this.projects.deleteVersion(this.underlying)
                                   else IO.pure(project))

  override def key: String = this.project.url + '/' + this.underlying.versionString

  def dependenciesAsGhostTags: Seq[VersionTag] =
    Platform.ghostTags(-1L, this.underlying.dependencies)

}
