package ore.project.factory

import db.ModelService
import db.impl.access.ProjectBase
import models.project.{Project, ProjectSettings, Version}
import models.user.role.ProjectRole
import ore.project.io.PluginFile
import ore.{Cacheable, OreConfig}
import play.api.cache.SyncCacheApi
import scala.concurrent.{ExecutionContext, Future}

import discourse.OreDiscourseApi

/**
  * Represents a Project with an uploaded plugin that has not yet been
  * created.
  *
  * @param underlying  Pending project
  * @param file     Uploaded plugin
  */
case class PendingProject(projects: ProjectBase,
                          factory: ProjectFactory,
                          underlying: Project,
                          file: PluginFile,
                          channelName: String,
                          settings: ProjectSettings = ProjectSettings(),
                          var pendingVersion: PendingVersion,
                          roles: Set[ProjectRole] = Set(),
                          cacheApi: SyncCacheApi)
                         (implicit service: ModelService, val config: OreConfig)
                           extends Cacheable {

  def complete()(implicit ec: ExecutionContext): Future[(Project, Version)] = {
    free()
    for {
      newProject <- this.factory.createProject(this)
      newVersion <- {
        this.pendingVersion.project = newProject
        this.factory.createVersion(this.pendingVersion)
      }
      updatedProject <- service.update(newProject.copy(recommendedVersionId = Some(newVersion._1.id.value)))
    } yield (updatedProject, newVersion._1)
  }

  def cancel()(implicit ec: ExecutionContext, forums: OreDiscourseApi) = {
    free()
    this.file.delete()
    if (this.underlying.isDefined)
      this.projects.delete(this.underlying)
  }

  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
object PendingProject {
  def createPendingVersion(project: PendingProject): PendingVersion = {
    val result = project.factory.startVersion(project.file, project.underlying, project.settings, project.channelName)
    result match {
      case Right (version) =>
        version.cache()
        version
      // TODO: not this crap
      case Left (errorMessage) => throw new IllegalArgumentException(errorMessage)
    }
  }
}
