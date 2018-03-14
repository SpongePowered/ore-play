package ore.project.factory

import db.ModelService
import db.impl.TagTable
import db.impl.access.ProjectBase
import models.project.{Project, ProjectSettings, Tag}
import models.user.role.ProjectRole
import ore.project.Dependency._
import ore.project.io.PluginFile
import ore.{Cacheable, Colors, OreConfig}
import play.api.cache.SyncCacheApi
import util.PendingAction

import scala.util.Try

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
                          extends PendingAction[Project]
                            with Cacheable {

  /**
    * The [[Project]]'s internal settings.
    */
  val settings: ProjectSettings = this.service.processor.process(ProjectSettings())

  /**
    * The first [[PendingVersion]] for this PendingProject.
    */
  val pendingVersion: PendingVersion = {
    val version = this.factory.startVersion(this.file, this.underlying, this.channelName)
    val model = version.underlying
    version.cache()
    version
  }

  override def complete(): Try[Project] = Try {
    free()
    val newProject = this.factory.createProject(this).get
    this.pendingVersion.project = newProject
    val newVersion = this.factory.createVersion(this.pendingVersion).get
    newProject.recommendedVersion = newVersion
    newProject
  }

  override def cancel() = {
    free()
    this.file.delete()
    if (this.underlying.isDefined)
      this.projects.delete(this.underlying)
  }

  override def key: String = this.underlying.ownerName + '/' + this.underlying.slug

}
