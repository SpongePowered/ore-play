package ore.project.factory

import scala.concurrent.ExecutionContext

import play.api.cache.SyncCacheApi

import db.{DbRef, InsertFunc, ModelService}
import db.impl.schema.VersionTable
import db.impl.OrePostgresDriver.api._
import models.project._
import models.user.User
import ore.project.Dependency
import ore.project.io.PluginFileWithData
import ore.{Cacheable, Color, Platform}

import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Represents a pending version to be created later.
  *
  * @param channelName    Name of channel this version will be in
  * @param channelColor   Color of channel for this version
  * @param plugin         Uploaded plugin
  */
case class PendingVersion(
    versionString: String,
    dependencyIds: List[String],
    description: Option[String],
    projectId: Option[DbRef[Project]], // Version might be for an uncreated project
    fileSize: Long,
    hash: String,
    fileName: String,
    signatureFileName: String,
    authorId: DbRef[User],
    projectUrl: String,
    channelName: String,
    channelColor: Color,
    plugin: PluginFileWithData,
    createForumPost: Boolean,
    cacheApi: SyncCacheApi
) extends Cacheable {

  def complete(
      project: Project,
      factory: ProjectFactory
  )(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[(Version, Channel, Seq[VersionTag])] =
    free *> factory.createVersion(project, this)

  override def key: String = projectUrl + '/' + versionString

  def dependencies: List[Dependency] =
    for (depend <- dependencyIds) yield {
      val data = depend.split(":", 2)
      Dependency(data(0), if (data.length > 1) data(1) else "")
    }

  def dependenciesAsGhostTags: Seq[InsertFunc[VersionTag]] =
    Platform.ghostTags(-1L, dependencies)

  /**
    * Returns true if a project ID is defined on this Model, there is no
    * matching hash in the Project, and there is no duplicate version with
    * the same name in the Project.
    *
    * @return True if exists
    */
  def exists(implicit service: ModelService): IO[Boolean] = {
    def hashExists(projectId: DbRef[Project]) = {
      val baseQuery = for {
        v <- TableQuery[VersionTable]
        if v.projectId === projectId
        if v.hash === hash
      } yield v.id

      service.runDBIO((baseQuery.length > 0).result)
    }

    projectId.fold(IO.pure(false)) { projectId =>
      for {
        hashExists <- hashExists(projectId)
        project    <- service.get[Project](projectId).getOrElse(sys.error(s"No project found for id $projectId"))
        pExists    <- project.versions.exists(_.versionString.toLowerCase === this.versionString.toLowerCase)
      } yield hashExists && pExists
    }
  }

  def asFunc(projectId: DbRef[Project], channelId: DbRef[Channel]): InsertFunc[Version] = Version.partial(
    versionString = versionString,
    dependencyIds = dependencyIds,
    description = description,
    projectId = projectId,
    channelId = channelId,
    fileSize = fileSize,
    hash = hash,
    authorId = authorId,
    fileName = fileName,
    signatureFileName = signatureFileName
  )
}
