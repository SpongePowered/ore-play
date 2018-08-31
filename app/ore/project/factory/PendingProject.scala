package ore.project.factory

import db.ModelService
import db.impl.access.ProjectBase
import models.project.{Project, ProjectSettings, Version}
import models.user.role.ProjectRole
import ore.project.io.PluginFile
import ore.{Cacheable, OreConfig}
import play.api.cache.SyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

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
                          implicit val config: OreConfig,
                          var roles: Set[ProjectRole] = Set(),
                          override val cacheApi: SyncCacheApi)
                         (implicit service: ModelService)
                           extends Cacheable {

  /**
    * The [[Project]]'s internal settings.
    */
  val settings: ProjectSettings = this.service.processor.process(ProjectSettings())

  /**
    * The first [[PendingVersion]] for this PendingProject.
    */
  val pendingVersion: PendingVersion = {
    val result = this.factory.startVersion(this.file, this.underlying, this.settings, this.channelName)
    result match {
      case Right (version) =>
        val model = version.underlying
        version.cache()
        version
        // TODO: not this crap
      case Left (errorMessage) => throw new IllegalArgumentException(errorMessage)
    }
  }

  def complete()(implicit ec: ExecutionContext): Future[(Project, Version)] = {
    free()
    for {
      newProject <- this.factory.createProject(this)
      newVersion <- {
        this.pendingVersion.project = newProject
        this.factory.createVersion(this.pendingVersion)
      }
      _ <- newProject.setRecommendedVersion(newVersion._1)
    } yield (newProject, newVersion._1)
  }

  def cancel()(implicit ec: ExecutionContext) = {
    free()
    this.file.delete()
    if (this.underlying.isDefined)
      this.projects.delete(this.underlying)
  }

  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
