package ore.project.factory

import java.sql.Timestamp
import java.time.Instant

import scala.concurrent.ExecutionContext

import play.api.cache.SyncCacheApi

import db.{DbRef, InsertFunc, ModelService}
import models.project.{Project, ProjectSettings, Version, Visibility}
import models.user.User
import models.user.role.ProjectUserRole
import ore.project.Category
import ore.project.io.PluginFileWithData
import ore.{Cacheable, OreConfig}

import cats.effect.{ContextShift, IO}

/**
  * Represents a Project with an uploaded plugin that has not yet been
  * created.
  */
case class PendingProject(
    pluginId: String,
    ownerName: String,
    ownerId: DbRef[User],
    name: String,
    slug: String,
    visibility: Visibility,
    file: PluginFileWithData,
    channelName: String,
    description: Option[String] = None,
    category: Category = Category.Undefined,
    settings: ProjectSettings.Partial = ProjectSettings.Partial(),
    var pendingVersion: PendingVersion,
    roles: Set[ProjectUserRole.Partial] = Set(),
    cacheApi: SyncCacheApi
)(implicit val config: OreConfig)
    extends Cacheable {

  def complete(factory: ProjectFactory)(
      implicit service: ModelService,
      ec: ExecutionContext,
      cs: ContextShift[IO]
  ): IO[(Project, Version)] =
    for {
      _              <- free
      newProject     <- factory.createProject(this)
      newVersion     <- factory.createVersion(newProject, this.pendingVersion)
      updatedProject <- service.update(newProject.copy(recommendedVersionId = Some(newVersion._1.id)))
    } yield (updatedProject, newVersion._1)

  def owner(implicit service: ModelService): IO[User] =
    service.getNow[User](ownerId).getOrElse(sys.error("No owner for pending project"))

  def asFunc: InsertFunc[Project] =
    Project.partial(
      pluginId,
      ownerName,
      ownerId,
      name,
      slug,
      visibility = visibility,
      lastUpdated = Timestamp.from(Instant.now()),
      description = description,
      category = category
    )

  override def key: String = ownerName + '/' + slug

}
object PendingProject {
  def createPendingVersion(factory: ProjectFactory, project: PendingProject): PendingVersion = {
    val result = factory.startVersion(
      project.file,
      project.pluginId,
      None,
      project.key,
      project.settings.forumSync,
      project.channelName
    )
    result match {
      case Right(version) =>
        version.cache.unsafeRunSync()
        version
      // TODO: not this crap
      case Left(errorMessage) => throw new IllegalArgumentException(errorMessage)
    }
  }
}
