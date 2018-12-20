package ore.project.factory

import scala.concurrent.ExecutionContext

import play.api.cache.SyncCacheApi

import db.ModelService
import db.impl.access.ProjectBase
import discourse.OreDiscourseApi
import models.project.{Project, ProjectSettings, Version}
import models.user.role.ProjectUserRole
import ore.project.io.PluginFileWithData
import ore.{Cacheable, OreConfig}

import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Represents a Project with an uploaded plugin that has not yet been
  * created.
  *
  * @param underlying  Pending project
  * @param file     Uploaded plugin
  */
case class PendingProject(
    projects: ProjectBase,
    factory: ProjectFactory,
    underlying: Project,
    file: PluginFileWithData,
    channelName: String,
    settings: ProjectSettings = ProjectSettings(),
    var pendingVersion: PendingVersion,
    roles: Set[ProjectUserRole] = Set(),
    cacheApi: SyncCacheApi
)(implicit service: ModelService, val config: OreConfig)
    extends Cacheable {

  def complete()(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[(Project, Version)] = {
    for {
      _          <- free
      newProject <- this.factory.createProject(this)
      newVersion <- {
        this.pendingVersion.project = newProject
        this.factory.createVersion(this.pendingVersion)
      }
      updatedProject <- service.update(newProject.copy(recommendedVersionId = Some(newVersion._1.id.value)))
    } yield (updatedProject, newVersion._1)
  }

  def cancel()(implicit forums: OreDiscourseApi): IO[Unit] =
    free *> this.file.delete *> (if (this.underlying.isDefined) this.projects.delete(this.underlying).void
                                 else IO.unit)

  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
object PendingProject {
  def createPendingVersion(project: PendingProject): PendingVersion = {
    val result = project.factory.startVersion(project.file, project.underlying, project.settings, project.channelName)
    result match {
      case Right(version) =>
        version.cache.unsafeRunSync()
        version
      // TODO: not this crap
      case Left(errorMessage) => throw new IllegalArgumentException(errorMessage)
    }
  }
}
