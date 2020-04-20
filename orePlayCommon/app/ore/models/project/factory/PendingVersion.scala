package ore.models.project.factory

import scala.language.higherKinds

import play.api.cache.SyncCacheApi

import ore.Cacheable
import ore.data.project.Dependency
import ore.data.{Color, Platform}
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.VersionTable
import ore.db.{DbRef, Model, ModelService}
import ore.models.project._
import ore.models.project.io.PluginFileWithData
import ore.models.user.User

import cats.MonadError
import cats.syntax.all._
import slick.lifted.TableQuery
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{Task, ZIO}

/**
  * Represents a pending version to be created later.
  *
  * @param channelName    Name of channel this version will be in
  * @param channelColor   Color of channel for this version
  * @param plugin         Uploaded plugin
  */
case class PendingVersion(
    versionString: String,
    dependencies: List[Dependency],
    description: Option[String],
    projectId: DbRef[Project],
    fileSize: Long,
    hash: String,
    fileName: String,
    authorId: DbRef[User],
    channelName: String,
    channelColor: Color,
    plugin: PluginFileWithData,
    createForumPost: Boolean,
    cacheApi: SyncCacheApi
) extends Cacheable {

  def complete(
      project: Model[Project],
      factory: ProjectFactory
  ): ZIO[Blocking, Nothing, (Model[Project], Model[Version], Model[Channel], Seq[Model[VersionTag]])] =
    free[Task].orDie *> factory.createVersion(project, this)

  override def key: String = s"$projectId/$versionString"

  def dependenciesAsGhostTags: Seq[VersionTag] =
    Platform.ghostTags(-1L, dependencies)

  /**
    * Returns true if a project ID is defined on this Model, there is no
    * matching hash in the Project, and there is no duplicate version with
    * the same name in the Project.
    *
    * @return True if exists
    */
  def exists[F[_]](implicit service: ModelService[F], F: MonadError[F, Throwable]): F[Boolean] = {
    val hashExistsBaseQuery = for {
      v <- TableQuery[VersionTable]
      if v.projectId === projectId
      if v.hash === hash
    } yield v.id

    val hashExistsQuery = hashExistsBaseQuery.exists

    for {
      project <- ModelView
        .now(Project)
        .get(projectId)
        .getOrElseF(F.raiseError(new Exception(s"No project found for id $projectId")))
      versionExistsQuery = project
        .versions(ModelView.later(Version))
        .exists(_.versionString.toLowerCase === this.versionString.toLowerCase)
      res <- service.runDBIO(Query(hashExistsQuery || versionExistsQuery).result.head)
    } yield res
  }

  def asVersion(projectId: DbRef[Project], channelId: DbRef[Channel]): Version = Version(
    versionString = versionString,
    dependencyIds = dependencies.map {
      case Dependency(pluginId, Some(version)) => s"$pluginId:$version"
      case Dependency(pluginId, None)          => pluginId
    },
    description = description,
    projectId = projectId,
    channelId = channelId,
    fileSize = fileSize,
    hash = hash,
    authorId = Some(authorId),
    fileName = fileName,
    createForumPost = createForumPost
  )
}
