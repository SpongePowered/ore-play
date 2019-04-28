package ore.models.project.factory

import scala.language.higherKinds

import java.time.Instant

import scala.concurrent.ExecutionContext

import play.api.cache.SyncCacheApi

import ore.data.project.Category
import ore.models.project.{Project, ProjectSettings, Version, Visibility}
import ore.models.user.User
import ore.models.user.role.ProjectUserRole
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}
import ore.models.project.io.PluginFileWithData
import ore.{Cacheable, OreConfig}

import cats.MonadError
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
    settings: ProjectSettings = ProjectSettings(0L), //Filled in later
    var pendingVersion: PendingVersion,
    roles: Set[ProjectUserRole] = Set(),
    cacheApi: SyncCacheApi
)(implicit val config: OreConfig)
    extends Cacheable {

  def complete(factory: ProjectFactory)(
      implicit service: ModelService[IO],
      ec: ExecutionContext,
      cs: ContextShift[IO]
  ): IO[(Model[Project], Model[Version])] =
    for {
      _              <- free
      newProject     <- factory.createProject(this)
      newVersion     <- factory.createVersion(newProject, this.pendingVersion)
      updatedProject <- service.update(newProject)(_.copy(recommendedVersionId = Some(newVersion._1.id)))
    } yield (updatedProject, newVersion._1)

  def owner[F[_]](implicit service: ModelService[F], F: MonadError[F, Throwable]): F[Model[User]] =
    ModelView.now(User).get(ownerId).getOrElseF(F.raiseError(new Exception("No owner for pending project")))

  def asProject: Project =
    Project(
      pluginId,
      ownerName,
      ownerId,
      name,
      slug,
      visibility = visibility,
      lastUpdated = Instant.now(),
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
